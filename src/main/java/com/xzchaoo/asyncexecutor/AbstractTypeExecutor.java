package com.xzchaoo.asyncexecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public abstract class AbstractTypeExecutor implements TypedExecutor {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected void safeExecute(Runnable asyncTask) {
        try {
            asyncTask.run();
        } catch (Throwable e) {
            // 强调一下不应该抛异常, 不过现在这个方法几乎不可能抛异常了, 因为这里提供的Runnable由我们自己控制
            logger.error("asyncTask run error, user tasks must handle all Throwable exceptions.", e);
        }
    }

    @Override
    public Stat stat() {
        return null;
    }

    @Override
    public void execute(int type, Consumer<Ack> asyncTaskWithAck) {
        if (type <= 0) {
            throw new IllegalArgumentException("type <= 0");
        }
        if (asyncTaskWithAck == null) {
            throw new IllegalArgumentException("asyncTaskWithAck is null");
        }
        execute(type, () -> { //
            AtomicAck ack = new AtomicAck(type);
            try {
                asyncTaskWithAck.accept(ack);
            } catch (Throwable e) {
                ack.ack();
                logger.error("asyncTaskWithAck error", e);
            }
        });
    }

    @Override
    public boolean tryExecute(int type, Consumer<Ack> asyncTaskWithAck) {
        if (type <= 0) {
            throw new IllegalArgumentException("type <= 0");
        }
        if (asyncTaskWithAck == null) {
            throw new IllegalArgumentException("asyncTaskWithAck is null");
        }
        return tryExecute(type, () -> { //
            AtomicAck ack = new AtomicAck(type);
            try {
                asyncTaskWithAck.accept(ack);
            } catch (Throwable e) {
                ack.ack();
                logger.error("asyncTaskWithAck error", e);
            }
        });
    }

    @Override
    public void execute(int type, Executor executor, Runnable syncTask) {
        if (type <= 0) {
            throw new IllegalArgumentException("type <= 0");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor is null");
        }
        if (syncTask == null) {
            throw new IllegalArgumentException("sync task is null");
        }
        execute(type, () -> {
            try {
                executor.execute(() -> {
                    try {
                        syncTask.run();
                    } finally {
                        ack(type);
                    }
                });
            } catch (Throwable e) {
                ack(type);
                logger.error("executor syncTaskWithAck error", e);
            }
        });
    }

    /**
     * @param type          type
     * @param safeAsyncTask safeAsyncTask
     */
    protected abstract void execute(int type, Runnable safeAsyncTask);

    protected abstract boolean tryExecute(int type, Runnable safeAsyncTask);

    /**
     * Ack
     *
     * @param type
     */
    protected abstract void ack(int type);

    private static final AtomicIntegerFieldUpdater<AtomicAck> DONE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(AtomicAck.class, "done");

    protected class AtomicAck implements Ack {

        volatile int done;
        final    int type;

        AtomicAck(int type) {
            this.type = type;
        }

        @Override
        public void ack() {
            if (DONE_UPDATER.compareAndSet(this, 0, 1)) {
                AbstractTypeExecutor.this.ack(type);
            }
        }
    }
}
