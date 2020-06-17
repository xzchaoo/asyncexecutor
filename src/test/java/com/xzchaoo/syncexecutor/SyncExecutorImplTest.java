package com.xzchaoo.syncexecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-17
 */
public class SyncExecutorImplTest {
    @Test
    public void test() throws InterruptedException {
        SyncExecutor se = new SyncExecutorImpl();
        ExecutorService es = Executors.newFixedThreadPool(16);
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 10000; i++) {
            int type = i % 2 + 1;
            es.execute(() -> {
                try {
                    se.acquire(type);
                    try {
                        Thread.sleep(1);
                    } finally {
                        se.ack(type);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    count.incrementAndGet();
                }
            });
        }
        for (int i = 0; i < 100; i++) {
            System.out.println(count.get());
            Thread.sleep(1000);
        }
    }
}
