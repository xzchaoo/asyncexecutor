package com.xzchaoo.asyncexecutor;

import org.junit.Test;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class RingBufferTest {
    @Test
    public void test() throws InterruptedException {
        // 创建一个环形队列
        WaitStrategy ws = new LiteBlockingWaitStrategy();
        RingBuffer<Event> rb = RingBuffer.createMultiProducer(Event::new, 128, ws);

        // 创建一个消费位点, 初始化是-1
        Sequence sequence = new Sequence();

        // 下面的调用让环境队列考虑sequence的消费进度, 必要时使用WaitStrategy等待消费进度前进(否则环形队列就覆盖元素了)
        rb.addGatingSequences(sequence);

        // 我们新启动一个线程默认consumer
        new Thread(() -> {
            SequenceBarrier barrier = rb.newBarrier();
            // nextCursor是我们期望可用的下标, 基于0
            long nextCursor = sequence.get() + 1;
            for (; ; ) {
                try {
                    // 等到 beginCursor 可用
                    // TODO 注意, 这里的返回值nextAvailableCursor, 有可能是 < nextCursor 的, 为什么? 好像是不可能的! 检查一下源代码
                    long nextAvailableCursor = barrier.waitFor(nextCursor);
                    // 所以这边加了一个判断, 如果不合法那么就继续循环等待
                    System.out.println("获得一个可用区间 [" + nextCursor + "," + nextAvailableCursor + "]");
                    while (nextCursor <= nextAvailableCursor) {
                        Event event = rb.get(nextCursor);
                        // 处理事件event
                        // System.out.println("处理事件 " + event);
                        nextCursor++;
                    }
                    // System.out.println("无效区间");
                    sequence.set(nextAvailableCursor);
                } catch (AlertException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (Throwable e) {
                    sequence.set(nextCursor);
                    // 跳过错误元素
                    nextCursor++;
                }
            }
        }).start();

        // 主线程模拟发布元素
        for (int i = 0; i < 256; i++) {
            long next = rb.next();
            rb.publish(next);
        }
        Thread.sleep(1000);
    }

    private static class Event {}
}
