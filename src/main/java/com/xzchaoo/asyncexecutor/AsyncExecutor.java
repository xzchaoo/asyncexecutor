package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
     * @param asyncCommandWithAck
     */
    default void publish(Consumer<Runnable> asyncCommandWithAck) {
        publish(() -> {
            AtomicBoolean b = new AtomicBoolean();
            asyncCommandWithAck.accept(() -> {
                if (b.compareAndSet(false, true)) {
                    ack();
                }
            });
        });
    }

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

    Stat stat();

    class Stat {
        /**
         * Get buffer size of this executor.
         *
         * @return
         */
        int bufferSize;

        /**
         * Get max concurrency of this executor.
         *
         * @return
         */
        int maxConcurrency;

        /**
         * Get delayed command count.
         *
         * @return
         */
        int delayedSize;

        /**
         * Get working in progress command count.
         *
         * @return
         */
        int activeCount;

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getDelayedSize() {
            return delayedSize;
        }

        public void setDelayedSize(int delayedSize) {
            this.delayedSize = delayedSize;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public void setActiveCount(int activeCount) {
            this.activeCount = activeCount;
        }
    }
}
