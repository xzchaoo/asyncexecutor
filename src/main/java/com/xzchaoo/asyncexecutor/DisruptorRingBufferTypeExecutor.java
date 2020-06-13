package com.xzchaoo.asyncexecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class DisruptorRingBufferTypeExecutor extends AbstractTypeExecutor {

    private static final AtomicIntegerFieldUpdater<DisruptorRingBufferTypeExecutor> WIP_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(DisruptorRingBufferTypeExecutor.class, "wip");

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
    private final RingBuffer<TaskCommand>            cmdQueue;

    private final AtomicInteger   drainLoopWip = new AtomicInteger(0);
    private final SequenceBarrier barrier;
    private       Sequence        sequence     = new Sequence();
    private       long            nextCursor   = sequence.get() + 1;

    public DisruptorRingBufferTypeExecutor(int commandBufferSize, int bufferSize, int maxConcurrency, int maxBatch) {
        LiteBlockingWaitStrategy waitStrategy = new LiteBlockingWaitStrategy();
        this.cmdQueue = RingBuffer.createMultiProducer(TaskCommand::new, commandBufferSize, waitStrategy);
        this.cmdQueue.addGatingSequences(sequence);
        this.barrier = cmdQueue.newBarrier();
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
    public boolean tryExecute(int type, Runnable asyncTask) {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        if (asyncTask == null) {
            throw new IllegalArgumentException();
        }
        long cursor;
        try {
            cursor = cmdQueue.tryNext();
        } catch (InsufficientCapacityException e) {
            return false;
        }
        TaskCommand cmd = cmdQueue.get(cursor);
        cmd.type = type;
        cmd.task = asyncTask;
        cmdQueue.publish(cursor);
        drainLoop();
        return true;
    }

    @Override
    public void execute(int type, Runnable asyncTask) {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        if (asyncTask == null) {
            throw new IllegalArgumentException();
        }
        long cursor = cmdQueue.next();
        TaskCommand cmd = cmdQueue.get(cursor);
        cmd.type = type;
        cmd.task = asyncTask;
        cmdQueue.publish(cursor);
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
                try {
                    // TODO 不要使用waitFor? 它会阻塞
                    // long nextAvailableCursor = barrier.getCursor();
                    System.out.println("waitFor");
                    long nextAvailableCursor = barrier.waitFor(nextCursor);
                    System.out.println(String.format("[%d,%d]", nextCursor, nextAvailableCursor));
                    if (nextAvailableCursor < nextCursor) {
                        // System.out.println("没有元素了 " + nextAvailableCursor);
                        // 没有元素了
                        System.out.println("break");
                        break;
                    }
                    while (nextCursor <= nextAvailableCursor) {
                        TaskCommand cmd = this.cmdQueue.get(nextCursor);
                        process(cmd);
                        nextCursor++;
                    }
                    sequence.set(nextAvailableCursor);
                    // } catch (AlertException e) {
                    //     // TODO 先ignore
                    // } catch (InterruptedException e) {
                    //     // TODO 先ignore
                    // } catch (TimeoutException e) {
                    // TODO 先ignore
                } catch (Throwable e) {
                    e.printStackTrace();
                    // 跳过该错误元素
                    ++nextCursor;
                }
                // 其实非强制打到 maxBatch 才释放 可能会过早释放
                // 将wip放在后面可以让尽量多连续执行
                if (wip == 0) {
                    switchToOtherType();
                }
            }
            g = drainLoopWip.addAndGet(-g);
        } while (g != 0);
    }

    private void process(TaskCommand cmd) {
        System.out.println("process");
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

    private void delay(int type, Runnable task) {
        ArrayDeque<Runnable> q = delayed.get(type);
        if (q == null) {
            types.add(type);
            q = new ArrayDeque<>(bufferSize);
            delayed.put(type, q);
        }
        if (q.size() == bufferSize) {
            // TODO exception ? or ?
            throw new IllegalStateException("delay queue for " + type + " is full");
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
