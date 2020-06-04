package com.xzchaoo.asyncexecutor;

/**
 * created at 2020/6/4
 * @author xzchaoo
 */
public class AsyncExecutorConfig {
    /**
     * Required. Async Executor name, for thread pool.
     */
    private String  name                     = "default";
    /**
     * Max concurrency.
     */
    private int     maxConcurrency           = 256;
    /**
     * Disruptor buffer size.
     */
    private int     disruptorBufferSize      = 65536;
    /**
     * Delay command buffer size.
     */
    private int     delayedCommandBufferSize = 65536;
    /**
     * Whether block on insufficient capacity.
     */
    private boolean blockOnInsufficient      = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getDisruptorBufferSize() {
        return disruptorBufferSize;
    }

    public void setDisruptorBufferSize(int disruptorBufferSize) {
        this.disruptorBufferSize = disruptorBufferSize;
    }

    public int getDelayedCommandBufferSize() {
        return delayedCommandBufferSize;
    }

    public void setDelayedCommandBufferSize(int delayedCommandBufferSize) {
        this.delayedCommandBufferSize = delayedCommandBufferSize;
    }

    public boolean isBlockOnInsufficient() {
        return blockOnInsufficient;
    }

    public void setBlockOnInsufficient(boolean blockOnInsufficient) {
        this.blockOnInsufficient = blockOnInsufficient;
    }

    @Override
    public String toString() {
        return "AsyncExecutorConfig{" +
                "name='" + name + '\'' +
                ", maxConcurrency=" + maxConcurrency +
                ", disruptorBufferSize=" + disruptorBufferSize +
                ", delayedCommandBufferSize=" + delayedCommandBufferSize +
                ", blockOnInsufficient=" + blockOnInsufficient +
                '}';
    }
}
