package com.xzchaoo.syncexecutor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-17
 */
public class SyncExecutor {
    private final Lock      lock      = new ReentrantLock(false);
    // private Map<Integer, ArrayDeque<>>
    private       int       wip;
    private       int       executingType;
    private final Condition condition = lock.newCondition();

    public void acquire(int type) throws InterruptedException {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        lock.lock();
        try {
            for (; ; ) {
                if (executingType == 0) {
                    executingType = type;
                    ++wip;
                    break;
                } else if (executingType == type) {
                    ++wip;
                    break;
                } else {
                    condition.await();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void ack(int type) {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        lock.lock();
        try {
            if (executingType == type) {
                if (--wip == 0) {
                    executingType = 0;
                    // 全叫醒
                    condition.signalAll();
                }
            } else {
                throw new IllegalStateException();
            }
        } finally {
            lock.unlock();
        }
    }
}
