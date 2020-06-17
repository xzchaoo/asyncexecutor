package com.xzchaoo.asyncexecutor;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class TypeExecutors {
    /**
     * Lock free 非阻塞, buffer溢出则异常
     *
     * @param commandBufferSize
     * @param taskBufferSize
     * @param maxConcurrency
     * @param maxBatch
     * @return
     */
    public static TypedExecutor lockFree(int commandBufferSize, int taskBufferSize, int maxConcurrency, int maxBatch) {
        return new LockFreeTypeExecutor(commandBufferSize, taskBufferSize, maxConcurrency, maxBatch);
    }

    public static TypedExecutor liteBlockLockFree(int commandBufferSize, int taskBufferSize, int maxConcurrency, int maxBatch) {
        return new LiteBlockLockFreeTypeExecutor(commandBufferSize, taskBufferSize, maxConcurrency, maxBatch);
    }

    public static TypedExecutor disruptor(int commandBufferSize, int taskBufferSize, int maxConcurrency, int maxBatch) {
        return new DisruptorRingBufferTypeExecutor(commandBufferSize, taskBufferSize, maxConcurrency, maxBatch);
    }
}
