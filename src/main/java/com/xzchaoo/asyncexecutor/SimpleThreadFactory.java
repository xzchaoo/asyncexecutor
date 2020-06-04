package com.xzchaoo.asyncexecutor;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * created at 2020/6/4
 * @author xzchaoo
 */
class SimpleThreadFactory implements ThreadFactory {
    private final String        prefix;
    private final AtomicInteger index = new AtomicInteger();

    SimpleThreadFactory(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = prefix + "-" + index.getAndIncrement();
        return new Thread(r, name);
    }
}
