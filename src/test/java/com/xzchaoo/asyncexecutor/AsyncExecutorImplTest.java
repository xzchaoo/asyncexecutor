package com.xzchaoo.asyncexecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * created at 2020/6/4
 *
 * @author xzchaoo
 */
public class AsyncExecutorImplTest {
    @Test
    public void test() throws InterruptedException {
        AsyncExecutorConfig config = new AsyncExecutorConfig();
        config.setName("test");
        config.setMaxConcurrency(4);
        AsyncExecutor e = new AsyncExecutorImpl(config);
        e.start();
        ExecutorService es = Executors.newFixedThreadPool(4);
        AtomicInteger ai = new AtomicInteger();
        for (int i = 0; i < 20; i++) {
            e.publish(es, () -> {
                try {
                    Thread.sleep(1000);
                    System.out.println("睡觉" + ai.incrementAndGet());
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            });
        }
        while (ai.get() != 20) {
            Thread.sleep(1000);
        }
        e.stop();
    }
}
