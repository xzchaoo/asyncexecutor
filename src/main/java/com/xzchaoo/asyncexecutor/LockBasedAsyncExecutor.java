package com.xzchaoo.asyncexecutor;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-09
 */
public class LockBasedAsyncExecutor extends AbstractAsyncExecutor {
    private final Lock                 lock = new ReentrantLock();
    private final ArrayDeque<Runnable> delayed;
    private final int                  bufferSize;
    private final int                  maxConcurrency;
    private       int                  wip;

    public LockBasedAsyncExecutor(int bufferSize, int maxConcurrency) {
        this.bufferSize = bufferSize;
        this.maxConcurrency = maxConcurrency;
        this.delayed = new ArrayDeque<>(bufferSize);
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
        lock.lock();
        try {
            if (delayed.size() == bufferSize) {
                throw new IllegalStateException("delayed buffer is full");
            }
            if (wip < maxConcurrency && delayed.isEmpty()) {
                ++wip;
                execute(asyncCommand);
                return;
            }
            delayed.offerLast(asyncCommand);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void ack() {
        lock.lock();
        try {
            if (wip == 0) {
                throw new IllegalStateException("wip is 0");
            }
            Runnable asyncCommand = delayed.pollFirst();
            if (asyncCommand != null) {
                execute(asyncCommand);
            } else {
                --wip;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Stat stat() {
        lock.lock();
        try {
            Stat stat = new Stat();
            stat.activeCount = wip;
            stat.delayedSize = delayed.size();
            stat.bufferSize = bufferSize;
            stat.maxConcurrency = maxConcurrency;
            return stat;
        } finally {
            lock.unlock();
        }
    }
}
