package com.xzchaoo.asyncexecutor;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

/**
 * created at 2020/6/4
 * @author xzchaoo
 */
public class AsyncExecutorImpl implements AsyncExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncExecutorImpl.class);

    private static final int STATE_NEW     = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_STOPPED = 2;

    private static final AtomicIntegerFieldUpdater<AsyncExecutorImpl> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AsyncExecutorImpl.class, "state");

    private static final int ACTION_ADD = 0;
    private static final int ACTION_ACK = 1;

    private final Disruptor<Event>     disruptor;
    private final RingBuffer<Event>    ringBuffer;
    private final int                  maxConcurrency;
    private final int                  delayedCommandBufferSize;
    private final boolean              blockOnInsufficient;
    private final ArrayDeque<Runnable> delayed;

    private int wip;

    private volatile int state = STATE_NEW;

    public AsyncExecutorImpl(AsyncExecutorConfig config) {
        if (config.getMaxConcurrency() <= 0) {
            throw new IllegalArgumentException("maxConcurrency must > 0");
        }
        this.maxConcurrency = config.getMaxConcurrency();
        if (config.getDelayedCommandBufferSize() <= 0) {
            throw new IllegalArgumentException("delayedCommandBufferSize must > 0");
        }
        this.delayedCommandBufferSize = config.getDelayedCommandBufferSize();
        this.blockOnInsufficient = config.isBlockOnInsufficient();
        if (config.getDisruptorBufferSize() <= 0) {
            throw new IllegalArgumentException("disruptorBufferSize must > 0");
        }
        delayed = new ArrayDeque<>(delayedCommandBufferSize);
        disruptor = new Disruptor<>(
                Event::new,
                config.getDisruptorBufferSize(),
                new SimpleThreadFactory(config.getName()),
                ProducerType.MULTI,
                new LiteBlockingWaitStrategy());
        disruptor.setDefaultExceptionHandler(new InnerExceptionHandler());
        disruptor.handleEventsWith((EventHandler<Event>) (event, sequence, endOfBatch) -> onEvent(event)) //
                // set to null, help gc
                .then((EventHandler<Event>) (event, sequence, endOfBatch) -> event.clear()); //
        ringBuffer = disruptor.getRingBuffer();
    }

    private void onEvent(Event event) {
        switch (event.action) {
            case ACTION_ADD:
                if (wip < maxConcurrency) {
                    ++wip;
                    execute(event.asyncCommand);
                } else {
                    if (delayed.size() == delayedCommandBufferSize) {
                        throw new IllegalStateException("delayedCommandBufferSize is full");
                    }
                    delayed.offerLast(event.asyncCommand);
                }
                break;
            case ACTION_ACK:
                Runnable asyncCommand = delayed.pollFirst();
                if (asyncCommand != null) {
                    execute(asyncCommand);
                } else {
                    if (wip == 0) {
                        throw new IllegalStateException("wip is 0");
                    }
                    --wip;
                }
                break;
        }
    }

    private void execute(Runnable asyncCommand) {
        try {
            asyncCommand.run();
        } catch (Exception e) {
            // 按约定 不应该抛异常, 我们只能记录error然后忽略
            LOGGER.error("uncaught exception", e);
        }
    }

    @Override
    public void start() {
        if (STATE_UPDATER.compareAndSet(this, STATE_NEW, STATE_STARTED)) {
            disruptor.start();
        } else {
            if (state == STATE_STARTED) {
                LOGGER.error("fail to start AsyncExecutor, current state is STARTED.");
            } else {
                LOGGER.error("fail to start AsyncExecutor, current state is STOPPED.");
            }
        }
    }

    @Override
    public void stop() {
        if (STATE_UPDATER.compareAndSet(this, STATE_STARTED, STATE_STOPPED)) {
            disruptor.shutdown();
        } else {
            if (state == STATE_NEW) {
                LOGGER.error("fail to stop AsyncExecutor, current state is NEW.");
            } else {
                LOGGER.error("fail to stop AsyncExecutor, current state is STOPPED.");
            }
        }
    }

    @Override
    public void publish(Runnable asyncCommand) {
        if (asyncCommand == null) {
            throw new IllegalArgumentException("asyncCommand is null");
        }
        if (state != STATE_STARTED) {
            throw new IllegalStateException("this AsyncExecutor has stopped");
        }
        add0(ACTION_ADD, asyncCommand, true);
    }

    @Override
    public void ack() {
        if (state != STATE_STARTED) {
            // TODO exception or just ignore?
            throw new IllegalStateException("this AsyncExecutor has stopped");
        }
        add0(ACTION_ACK, null, false);
    }

    private void add0(int action, Runnable command, boolean canBlock) {
        long cursor;
        if (blockOnInsufficient && canBlock) {
            cursor = ringBuffer.next();
        } else {
            try {
                cursor = ringBuffer.tryNext();
            } catch (InsufficientCapacityException e) {
                throw new IllegalStateException(e);
            }
        }
        Event event = ringBuffer.get(cursor);
        event.action = action;
        event.asyncCommand = command;
        ringBuffer.publish(cursor);
    }

    @Override
    public int getDelaySize() {
        // TODO 理论上非线程安全 但不影响功能 只是不准而已
        return delayed.size();
    }

    @Override
    public int getActiveCount() {
        return wip;
    }

    private static class Event {
        int      action;
        Runnable asyncCommand;

        void clear() {
            action = 0;
            asyncCommand = null;
        }
    }

    private static class InnerExceptionHandler implements ExceptionHandler<Event> {
        @Override
        public void handleEventException(Throwable ex, long sequence, Event event) {
            LOGGER.error("handleEventException", ex);
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            LOGGER.error("handleOnStartException", ex);
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            LOGGER.error("handleOnShutdownException", ex);
        }
    }

}
