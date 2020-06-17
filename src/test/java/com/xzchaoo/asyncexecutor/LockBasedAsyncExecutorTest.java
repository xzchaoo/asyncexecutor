package com.xzchaoo.asyncexecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class LockBasedAsyncExecutorTest {
    @Test
    public void test() throws InterruptedException {
        // AsyncExecutor ae = AsyncExecutors.lockBased(10, 4);
        // TODO 注意 bufferSize必须是2^n
        AsyncExecutor ae = AsyncExecutors.lockFree(65536, 64);
        ae.start();
        ExecutorService es = Executors.newFixedThreadPool(64);
        AtomicInteger ai = new AtomicInteger();
        // Executors.newFixedThreadPool(8)
        for (int i = 0; i < 6400; i++) {
            ae.publish(es, () -> {
                try {
                    Thread.sleep(10);
                    ai.incrementAndGet();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        Thread.sleep(5000);
        System.out.println(ai.get());
    }
}
