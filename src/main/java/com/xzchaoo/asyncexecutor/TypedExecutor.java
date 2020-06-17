package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * TODO 不知道叫啥名字好了
 *
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public interface TypedExecutor {
    /**
     * Start this instance.
     */
    void start();

    /**
     * Start this instance.
     */
    void stop();

    /**
     * @param type
     * @param asyncTask
     */
    void execute(int type, Consumer<Ack> asyncTask);

    boolean tryExecute(int type, Consumer<Ack> asyncTask);

    // void safeExecute(int type, Consumer<Ack> asyncTask, Runnable onQueueFullCallback);

    void execute(int type, Executor executor, Runnable syncTask);

    Stat stat();

    /**
     * Ack interface.
     */
    @FunctionalInterface
    interface Ack {
        void ack();
    }

    interface LimitProvider {
        // void onLimit(int type, Runnable asyncTask);

        /**
         * Return max concurrency of type, must > 0.
         *
         * @param type
         * @return
         */
        int getMaxConcurrency(int type);

        /**
         * Return max batch of type, value <= 0 means no limit.
         *
         * @param type
         * @return
         */
        int getMaxBatch(int type);

        /**
         * 此时已经无法对应... 任务了... 难道要多附加一个
         *
         * @param type
         * @param asyncTask
         */
        // @Deprecated
        // void onQueueFull(int type, Runnable asyncTask);
    }

    class Stat {
        private int delayedSize;
        private int wip;

        public int getDelayedSize() {
            return delayedSize;
        }

        public void setDelayedSize(int delayedSize) {
            this.delayedSize = delayedSize;
        }

        public int getWip() {
            return wip;
        }

        public void setWip(int wip) {
            this.wip = wip;
        }
    }
}
