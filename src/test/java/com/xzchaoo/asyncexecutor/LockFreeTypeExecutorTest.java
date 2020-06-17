package com.xzchaoo.asyncexecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class LockFreeTypeExecutorTest {
    @Test
    public void test() throws InterruptedException {
        // 应对突然爆发的流量可能不是很友好
        TypedExecutor e = TypedExecutors.lockFree(65536, 128, 4096);
        e.start();
        ThreadPoolExecutor es = new ThreadPoolExecutor(128, 128, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8192 * 2),
                new ThreadPoolExecutor.AbortPolicy());
        es.prestartAllCoreThreads();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger wip = new AtomicInteger();
        ExecutorService emitter = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 65536 * 10; i++) {
            //for (int i = 0; i < 10; i++) {
            int type = i % 10 + 1;
            emitter.execute(() -> {
                e.execute(type, es, () -> {
                    try {
                        wip.incrementAndGet();
                        Thread.sleep(1);
                        count.incrementAndGet();
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    } finally {
                        wip.decrementAndGet();
                    }
                });
            });
        }
        for (int i = 0; i < 100; i++) {
            TypedExecutor.Stat stat = e.stat();
            System.out.println(stat.getDelayedSize() + " " + count.get() + " " + stat.getWip() + " " + wip.get());
            Thread.sleep(1000);
        }
    }
}
