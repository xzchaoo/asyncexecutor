package com.xzchaoo.asyncexecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jctools.queues.MpscArrayQueue;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */

public class LiteBlockLockFreeTypeExecutor extends AbstractTypeExecutor {

    private static final AtomicIntegerFieldUpdater<LiteBlockLockFreeTypeExecutor> WIP_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(LiteBlockLockFreeTypeExecutor.class, "wip");

    // configs
    private final int maxBatch;
    private final int maxConcurrency;
    private final int bufferSize;

    // state
    private          int executingType = 0;
    private          int executed;
    private volatile int wip;
    private volatile int delayedCount;

    // TODO 更优雅一些?
    private final Map<Integer, ArrayDeque<Runnable>> delayed = new HashMap<>();
    private final List<Integer>                      types   = new ArrayList<>();
    private final MpscArrayQueue<TaskCommand>        cmdQueue;

    private final AtomicInteger drainLoopWip = new AtomicInteger(0);

    public LiteBlockLockFreeTypeExecutor(int commandBufferSize, int bufferSize, int maxConcurrency, int maxBatch) {
        this.cmdQueue = new MpscArrayQueue<>(commandBufferSize);
        this.bufferSize = bufferSize;
        this.maxConcurrency = maxConcurrency;
        this.maxBatch = maxBatch;
    }

    @Override
    public void start() {
        // nothing todo
    }

    @Override
    public void stop() {
        // nothing todo
    }

    @Override
    public void execute(int type, Runnable asyncTask) {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        if (asyncTask == null) {
            throw new IllegalArgumentException();
        }
        TaskCommand cmd = new TaskCommand();
        cmd.type = type;
        cmd.task = asyncTask;
        if (!cmdQueue.offer(cmd)) {
            lock.lock();
            needNotify.set(true);
            try {
                for (; !cmdQueue.offer(cmd); ) {
                    if (needNotify.get()) {
                        try {
                            free.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("await interrupted", e);
                        }
                    }
                    // block ?
                    // throw new IllegalStateException("cmdQueue is full " + cmdQueue.size());
                }
            } finally {
                lock.unlock();
            }
        }
        drainLoop();
    }

    @Override
    protected boolean tryExecute(int type, Runnable safeAsyncTask) {
        throw new UnsupportedOperationException();
    }

    private final Lock          lock       = new ReentrantLock();
    private final Condition     free       = lock.newCondition();
    private final AtomicBoolean needNotify = new AtomicBoolean();

    public void executeNotBlock(int type, Runnable asyncTask) {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        if (asyncTask == null) {
            throw new IllegalArgumentException();
        }
        TaskCommand cmd = new TaskCommand();
        cmd.type = type;
        cmd.task = asyncTask;
        if (!cmdQueue.offer(cmd)) {
            // block ?
            throw new IllegalStateException("cmdQueue is full " + cmdQueue.size());
        }
        drainLoop();
    }

    private boolean canExecute() {
        return wip < maxConcurrency && executed < maxBatch;
    }

    private void drainLoop() {
        if (drainLoopWip.getAndIncrement() != 0) {
            return;
        }
        int g = drainLoopWip.get();
        do {
            for (; ; ) {
                TaskCommand cmd = cmdQueue.poll();
                if (cmd == null) {
                    // 没有任务了 break
                    break;
                }
                // 没有任务正在执行或当前批次可以继续执行
                if (executingType == 0 || executingType == cmd.type && canExecute()) {
                    executingType = cmd.type;
                    // 如果有积压先处理积压的
                    ArrayDeque<Runnable> q = delayed.get(cmd.type);
                    if (q != null) {
                        drainQueue(q);
                    }
                    if (q == null || canExecute()) {
                        ++executed;
                        WIP_UPDATER.incrementAndGet(this);
                        // 如果还能执行则立即执行
                        safeExecute(cmd.task);
                    } else {
                        // 否则加入延迟队列
                        delay(cmd.type, cmd.task);
                    }
                } else {
                    delay(cmd.type, cmd.task);
                }
            }
            // 其实非强制打到 maxBatch 才释放 可能会过早释放
            // 将wip放在后面可以让尽量多连续执行
            if (wip == 0) {
                switchToOtherType();
            }
            g = drainLoopWip.addAndGet(-g);
        } while (g != 0);
    }

    private void delay(int type, Runnable task) {
        ArrayDeque<Runnable> q = delayed.get(type);
        if (q == null) {
            types.add(type);
            q = new ArrayDeque<>(bufferSize);
            delayed.put(type, q);
        }
        if (q.size() == bufferSize) {
            // TODO exception ? or ?
            // throw new IllegalStateException("delay queue for " + type + " is full");
            logger.error("delay queue for {} is full", type);
            return;
        }
        ++delayedCount;
        q.add(task);

    }

    private int typeIndex = -1;

    private void switchToOtherType() {
        executingType = 0;
        WIP_UPDATER.lazySet(this, 0);
        executed = 0;
        if (delayedCount == 0) {
            return;
        }
        int size = types.size();
        for (int i = 0; i < size; ++i) {
            typeIndex++;
            if (typeIndex == size) {
                typeIndex = 0;
            }
            Integer type = types.get(typeIndex);
            ArrayDeque<Runnable> q = delayed.get(type);
            if (!q.isEmpty()) {
                executingType = type;
                drainQueue(q);
                return;
            }
        }
    }

    private void drainQueue(ArrayDeque<Runnable> q) {
        for (; canExecute(); ) {
            Runnable task = q.pollFirst();
            if (task == null) {
                break;
            }
            WIP_UPDATER.incrementAndGet(this);
            ++executed;
            --delayedCount;
            safeExecute(task);
        }
    }

    @Override
    public void ack(int type) {
        // TODO block here?
        // TODO 这里假设所有ack调用都是合法的, 否则可能会有问题...
        // 这个 executingType 在错误ack情况下的可见性无法保证
        if (executingType != type) {
            throw new IllegalStateException("executingType mismatch");
        }

        for (; ; ) {
            int wip = this.wip;
            if (wip <= 0) {
                throw new IllegalStateException("wip <= 0");
            }
            if (WIP_UPDATER.compareAndSet(this, wip, wip - 1)) {
                break;
            }
        }

        // TODO 用 waitStrategy 封装
        if (needNotify.compareAndSet(false, true)) {
            lock.lock();
            try {
                free.signal();
            } finally {
                lock.unlock();
            }
        }

        drainLoop();
    }

    @Override
    public Stat stat() {
        Stat stat = new Stat();
        stat.setDelayedSize(delayedCount);
        return stat;
    }

    private static class TaskCommand {
        int      type;
        Runnable task;
    }
}
