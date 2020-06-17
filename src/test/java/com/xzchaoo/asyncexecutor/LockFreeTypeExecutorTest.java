package com.xzchaoo.asyncexecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class LockFreeTypeExecutorTest {
    @Test
    public void test() throws InterruptedException {
        TypeExecutor e = TypeExecutors.disruptor(65536, 65536, 128, 256);
        e.start();
        ExecutorService es = Executors.newFixedThreadPool(128);
        AtomicInteger count = new AtomicInteger();
        AtomicInteger wip = new AtomicInteger();
        ExecutorService emitter = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 10000; i++) {
            //for (int i = 0; i < 10; i++) {
            int type = i % 10 + 1;
            emitter.execute(() -> {
                e.execute(type, ack -> {
                    es.execute(() -> {
                        try {
                            wip.incrementAndGet();
                            Thread.sleep(1);
                            count.incrementAndGet();
                            // System.out.println("任务" + type);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        } finally {
                            wip.decrementAndGet();
                            ack.run();
                        }
                    });
                });
            });
        }
        for (int i = 0; i < 100; i++) {
            System.out.println(e.stat().getDelayedSize() + " " + count.get() + " " + wip.get());
            Thread.sleep(1000);
        }
    }
}
