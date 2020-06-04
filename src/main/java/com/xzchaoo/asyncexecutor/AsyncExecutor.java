package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author xiangfeng.xzc
 */
public interface AsyncExecutor {
    /**
     * Start
     */
    void start();

    /**
     * Stop
     */
    void stop();

    /**
     * Publish an async command into this executor. This async command get ran when there is available concurrency remaining.
     *
     * @param asyncCommand
     */
    void publish(Runnable asyncCommand);

    /**
     * Wrap a sync command into async command by running sync command in an executor.
     *
     * @param executor
     * @param syncCommand
     */
    default void publish(Executor executor, Runnable syncCommand) {
        publish(() -> {
            try {
                executor.execute(() -> {
                    try {
                        syncCommand.run();
                    } finally {
                        ack();
                    }
                });
            } catch (RejectedExecutionException e) {
                ack();
            }
        });
    }

    /**
     * Ack that an async command is done.
     */
    void ack();

    int getDelaySize();
}
