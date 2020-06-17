package com.xzchaoo.asyncexecutor;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public final class AsyncExecutors {
    private AsyncExecutors() {}
    
    public static AsyncExecutor lockFree(int bufferSize, int maxConcurrency) {
        return new LockFreeAsyncExecutor(bufferSize, maxConcurrency);
    }
}
