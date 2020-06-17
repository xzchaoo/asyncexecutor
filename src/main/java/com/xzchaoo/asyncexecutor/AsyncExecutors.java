package com.xzchaoo.asyncexecutor;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public final class AsyncExecutors {
    private AsyncExecutors() {}

    public static AsyncExecutor lockBased(int bufferSize, int maxConcurrency) {
        return new LockBasedAsyncExecutor(bufferSize, maxConcurrency);
    }

    public static AsyncExecutor disruptor(String name, int bufferSize, int maxConcurrency) {
        AsyncExecutorConfig config = new AsyncExecutorConfig();
        config.setName(name);
        config.setDisruptorBufferSize(bufferSize);
        config.setDelayedCommandBufferSize(bufferSize);
        config.setMaxConcurrency(maxConcurrency);
        return new DisruptorAsyncExecutor(config);
    }

    public static AsyncExecutor lockFree(int bufferSize, int maxConcurrency) {
        return new LockFreeAsyncExecutor(bufferSize, maxConcurrency);
    }
}
