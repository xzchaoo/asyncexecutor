package com.xzchaoo.asyncexecutor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jctools.queues.MpscArrayQueue;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class LockFreeAsyncExecutor extends AbstractAsyncExecutor {
    private static final AtomicIntegerFieldUpdater<LockFreeAsyncExecutor> WIP_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(LockFreeAsyncExecutor.class, "wip");

    private final    MpscArrayQueue<Runnable> delayed;
    private final    int                      bufferSize;
    private final    int                      maxConcurrency;
    private volatile int                      wip;
    private final    AtomicInteger            drainLoopWip = new AtomicInteger();

    public LockFreeAsyncExecutor(int bufferSize, int maxConcurrency) {
        this.bufferSize = bufferSize;
        this.maxConcurrency = maxConcurrency;
        this.delayed = new MpscArrayQueue<>(bufferSize);
    }

    @Override
    public void start() {
        // nothing to do
    }

    @Override
    public void stop() {
        // nothing to do
    }

    @Override
    public void publish(Runnable asyncCommand) {
        if (!delayed.offer(asyncCommand)) {
            throw new IllegalStateException("delayed queue is full");
        }
        drainLoop();
    }

    private void drainLoop() {
        // 用于保证参与drainLoop的线程只有一个
        if (drainLoopWip.getAndIncrement() != 0) {
            return;
        }
        int g = drainLoopWip.get();
        do {
            for (; wip < maxConcurrency; ) {
                Runnable cmd = delayed.poll();
                if (cmd == null) {
                    break;
                }
                // 因为+的地方只有这么一个 肯定会成功的 并且不会超过 maxConcurrency
                WIP_UPDATER.incrementAndGet(this);
                execute(cmd);
            }
            g = drainLoopWip.addAndGet(-g);
        } while (g != 0);
    }

    @Override
    public void ack() {
        for (; ; ) {
            int wip = this.wip;
            if (wip <= 0) {
                throw new IllegalStateException("wip " + wip + " <= 0");
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
        stat.activeCount = wip;
        stat.delayedSize = delayed.size();
        stat.bufferSize = bufferSize;
        stat.maxConcurrency = maxConcurrency;
        return stat;
    }

}
