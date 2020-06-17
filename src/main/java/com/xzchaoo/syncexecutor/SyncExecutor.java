package com.xzchaoo.syncexecutor;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-17
 */
public interface SyncExecutor {
    boolean tryAcquire(int type);

    void acquire(int type) throws InterruptedException;

    void ack(int type);
}
