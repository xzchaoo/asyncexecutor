package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * TODO 不知道叫啥名字好了
 *
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public interface TypeExecutor {
    void start();

    void stop();

    boolean tryExecute(int type, Runnable asyncTask);

    void execute(int type, Runnable asyncTask);

    void ack(int type);

    Stat stat();

    class Stat {
        private int delayedSize;

        public int getDelayedSize() {
            return delayedSize;
        }

        public void setDelayedSize(int delayedSize) {
            this.delayedSize = delayedSize;
        }
    }

    default void execute(int type, Consumer<Runnable> asyncTaskWithAck) {
        if (asyncTaskWithAck == null) {
            throw new IllegalArgumentException();
        }
        execute(type, () -> { //
            asyncTaskWithAck.accept(() -> ack(type));
        });
    }

    default void execute(int type, Executor executor, Runnable syncTask) {
        if (executor == null) {
            throw new IllegalArgumentException();
        }
        if (syncTask == null) {
            throw new IllegalArgumentException();
        }
        execute(type, () -> { //
            try {
                executor.execute(syncTask);
            } catch (RejectedExecutionException e) {
                ack(type);
            }
        });
    }
}
