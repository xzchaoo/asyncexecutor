package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * TODO 不知道叫啥名字好了
 *
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public interface TypeExecutor {
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

    void execute(int type, Executor executor, Runnable syncTask);

    Stat stat();

    /**
     * Ack interface.
     */
    @FunctionalInterface
    interface Ack {
        void ack();
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
