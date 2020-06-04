package com.xzchaoo.asyncexecutor;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xiangfeng.xzc
 */
public class SimpleThreadFactory implements ThreadFactory {
    private final String        prefix;
    private final AtomicInteger index = new AtomicInteger();

    public SimpleThreadFactory(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = prefix + "-" + index.getAndIncrement();
        return new Thread(r, name);
    }
}
