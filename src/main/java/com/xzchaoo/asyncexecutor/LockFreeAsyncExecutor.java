package com.xzchaoo.asyncexecutor;

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscArrayQueue;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class LockFreeAsyncExecutor extends AbstractAsyncExecutor {

    private final MessagePassingQueue<Runnable> delayed;
    private final int                           maxConcurrency;
    private final AtomicInteger                 wip          = new AtomicInteger();
    private final AtomicInteger                 drainLoopWip = new AtomicInteger();

    public LockFreeAsyncExecutor(int bufferSize, int maxConcurrency) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize <= 0");
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency <= 0");
        }
        this.maxConcurrency = maxConcurrency;
        this.delayed = new MpscArrayQueue<>(bufferSize);
    }

    @Override
    protected void execute(Runnable asyncCommand) {
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
        int delta = drainLoopWip.get();
        do {
            for (; wip.get() < maxConcurrency; ) {
                Runnable cmd = delayed.relaxedPoll();
                if (cmd == null) {
                    break;
                }
                // 因为+的地方只有这么一个 并且不会超过 maxConcurrency
                wip.incrementAndGet();
                safeExecute(cmd);
            }
            delta = drainLoopWip.addAndGet(-delta);
        } while (delta != 0);
    }

    @Override
    protected void ack() {
        wip.decrementAndGet();
        drainLoop();
    }

    @Override
    public Stat stat() {
        Stat stat = new Stat();
        stat.activeCount = wip.get();
        stat.delayedSize = delayed.size();
        stat.bufferSize = delayed.capacity();
        stat.maxConcurrency = maxConcurrency;
        return stat;
    }

}
