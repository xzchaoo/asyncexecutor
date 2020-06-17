package com.xzchaoo.asyncexecutor;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public final class TypedExecutors {
    private TypedExecutors() {}

    public static TypedExecutor lockFree(int taskBufferSize, int maxConcurrency, int maxBatch) {
        return lockFree(65536, taskBufferSize, maxConcurrency, maxBatch);
    }

    /**
     * Lock free 非阻塞, buffer溢出则异常
     *
     * @param commandBufferSize
     * @param taskMaxDelaySize
     * @param maxConcurrency
     * @param maxBatch
     * @return
     */
    public static TypedExecutor lockFree(int commandBufferSize, int taskMaxDelaySize, int maxConcurrency, int maxBatch) {
        // commandBufferSize >= 瞬间爆发流量
        // taskBufferSize >= 瞬间爆发流量
        return new LockFreeTypeExecutor(commandBufferSize, taskMaxDelaySize, new TypedExecutor.LimitProvider() {
            @Override
            public int getMaxConcurrency(int type) {
                return maxConcurrency;
            }

            @Override
            public int getMaxBatch(int type) {
                return maxBatch;
            }
        });
    }

    public static TypedExecutor lockFree(int commandBufferSize, int taskBufferSize, TypedExecutor.LimitProvider limitProvider) {
        return new LockFreeTypeExecutor(commandBufferSize, taskBufferSize, limitProvider);
    }

    public static TypedExecutor liteBlockLockFree(int commandBufferSize, int taskBufferSize, int maxConcurrency, int maxBatch) {
        return new LiteBlockLockFreeTypeExecutor(commandBufferSize, taskBufferSize, maxConcurrency, maxBatch);
    }

}
