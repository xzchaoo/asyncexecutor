package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Async Executor with ability to limit concurrency.
 * created at 2020/6/4
 *
 * @author xzchaoo
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
     * @param executor    executor
     * @param syncCommand sync command
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

    /**
     * Get delayed command count.
     *
     * @return
     */
    int getDelaySize();

    /**
     * Get working in progress command count.
     *
     * @return
     */
    int getActiveCount();
}
