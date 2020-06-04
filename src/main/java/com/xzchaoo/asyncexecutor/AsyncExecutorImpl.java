package com.xzchaoo.asyncexecutor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

/**
 * @author xiangfeng.xzc
 */
public class AsyncExecutorImpl implements AsyncExecutor {
    private static final Logger               LOGGER     = LoggerFactory.getLogger(AsyncExecutorImpl.class);
    private static final int                  ACTION_ADD = 0;
    private static final int                  ACTION_ACK = 1;
    private final        Disruptor<Event>     disruptor;
    private final        RingBuffer<Event>    ringBuffer;
    private final        int                  maxConcurrency;
    private final        int                  delayCommandBuffer;
    private              int                  wip;
    private final        ArrayDeque<Runnable> delayBuffer;

    public AsyncExecutorImpl(String name,
                             int maxConcurrency,
                             int disruptorBuffer,
                             int delayCommandBuffer) {
        this.maxConcurrency = maxConcurrency;
        this.delayCommandBuffer = delayCommandBuffer;
        this.delayBuffer = new ArrayDeque<>(delayCommandBuffer);
        disruptor = new Disruptor<>(
                Event::new,
                disruptorBuffer,
                new SimpleThreadFactory(name),
                ProducerType.MULTI,
                new LiteBlockingWaitStrategy());
        // disruptor.setDefaultExceptionHandler(null);
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
                    if (delayBuffer.size() == delayCommandBuffer) {
                        throw new IllegalStateException("delayBuffer is full");
                    }
                    delayBuffer.add(event.asyncCommand);
                }
                break;
            case ACTION_ACK:
                Runnable asyncCommand = delayBuffer.pollFirst();
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
            LOGGER.error("uncaught exception", e);
        }
    }

    @Override
    public void start() {
        disruptor.start();
    }

    @Override
    public void stop() {
        disruptor.shutdown();
    }

    @Override
    public void publish(Runnable asyncCommand) {
        if (asyncCommand == null) {
            throw new IllegalArgumentException("asyncCommand is null");
        }
        long cursor;
        try {
            // TODO block on insufficient ?
            cursor = ringBuffer.tryNext();
        } catch (InsufficientCapacityException e) {
            throw new IllegalStateException(e);
        }
        Event event = ringBuffer.get(cursor);
        event.action = ACTION_ADD;
        event.asyncCommand = asyncCommand;
        ringBuffer.publish(cursor);
    }

    @Override
    public void ack() {
        long cursor;
        try {
            cursor = ringBuffer.tryNext();
        } catch (InsufficientCapacityException e) {
            throw new IllegalStateException(e);
        }
        Event event = ringBuffer.get(cursor);
        event.action = ACTION_ACK;
        event.asyncCommand = null;
        ringBuffer.publish(cursor);
    }

    @Override
    public int getDelaySize() {
        // TODO 理论上非线程安全 但不影响功能 只是不准而已
        return delayBuffer.size();
    }

    private static class Event {
        int      action;
        Runnable asyncCommand;

        void clear() {
            action = 0;
            asyncCommand = null;
        }
    }
}
