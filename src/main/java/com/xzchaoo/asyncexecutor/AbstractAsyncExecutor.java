package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-09
 */
public abstract class AbstractAsyncExecutor implements AsyncExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAsyncExecutor.class);

    protected void safeExecute(Runnable asyncCommand) {
        // 这个方法一定不能阻塞 否则出大事!
        try {
            asyncCommand.run();
        } catch (Throwable e) {
            // 按约定 不应该抛异常, 我们只能记录error然后忽略
            LOGGER.error("uncaught exception", e);
        }
    }

    @Override
    public void execute(Consumer<Ack> asyncTask) {
        execute(() -> {
            DefaultAck ack = new DefaultAck();
            try {
                asyncTask.accept(ack);
            } catch (Throwable e) {
                ack.ack();
                LOGGER.error("uncaught exception", e);
            }
        });
    }

    @Override
    public void execute(Executor executor, Runnable asyncTask) {
        execute(() -> {
            try {
                executor.execute(() -> {
                    try {
                        asyncTask.run();
                    } finally {
                        ack();
                    }
                });
            } catch (Throwable e) {
                ack();
                LOGGER.error("uncaught exception", e);
            }
        });
    }

    /**
     * @param asyncTask
     */
    protected abstract void execute(Runnable asyncTask);

    /**
     * Ack
     */
    protected abstract void ack();

    // 减少一个引用
    private static final AtomicIntegerFieldUpdater<DefaultAck> DONE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(DefaultAck.class, "done");

    protected class DefaultAck implements Ack {
        volatile int done;

        @Override
        public void ack() {
            if (DONE_UPDATER.compareAndSet(this, 0, 1)) {
                AbstractAsyncExecutor.this.ack();
            }
        }
    }
}
