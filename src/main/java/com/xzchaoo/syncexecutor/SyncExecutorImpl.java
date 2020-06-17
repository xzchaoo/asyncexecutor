package com.xzchaoo.syncexecutor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-17
 */
public class SyncExecutorImpl implements SyncExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncExecutorImpl.class);

    private final Lock      lock      = new ReentrantLock(false);
    private       int       wip;
    private       int       executingType;
    private final Condition condition = lock.newCondition();

    @Override
    public boolean tryAcquire(int type) {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        // 这部分的锁估计很难去掉
        lock.lock();
        try {
            if (executingType == 0) {
                executingType = type;
                wip = ++this.wip;
                return true;
            } else if (executingType == type) {
                wip = ++this.wip;
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public void acquire(int type) throws InterruptedException {
        if (type <= 0) {
            throw new IllegalArgumentException();
        }
        lock.lock();
        try {
            for (; ; ) {
                if (executingType == 0) {
                    executingType = type;
                    ++this.wip;
                    break;
                } else if (executingType == type) {
                    ++this.wip;
                    break;
                } else {
                    condition.await();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
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
                throw new IllegalStateException("executingType " + executingType + " mismatch type " + type);
            }
        } finally {
            lock.unlock();
        }
    }
}
