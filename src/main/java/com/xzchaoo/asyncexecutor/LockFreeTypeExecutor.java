package com.xzchaoo.asyncexecutor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.queues.MpscArrayQueue;

/**
 * 缺点加入command队列就算是成功, 但其实后面可能会因为加不进去队列而失败...
 *
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public class LockFreeTypeExecutor extends AbstractTypeExecutor {

    // configs
    private final int bufferSize;

    private final LimitProvider limitProvider;

    // state
    /**
     * Current executing type.
     */
    private int           executingType = 0;
    private int           maxBatch;
    private int           maxConcurrency;
    /**
     * Current executed task count of currentType.
     */
    private int           executed;
    /**
     * Current work in progress task count of currentType.
     */
    private AtomicInteger wip           = new AtomicInteger();
    /**
     * Total delayed task count of all types.
     */
    private AtomicInteger delayedCount  = new AtomicInteger();
    /**
     * Current executingType's index of types array.
     */
    private int           typeIndex     = -1;
    /**
     * Indicate that other type has delayed tasks. Current type should give up when 'executed' reach 'maxBatch'.
     */
    private boolean       otherTypeHasDelayed;

    private final ConcurrentHashMap<Integer, MpscArrayQueue<Runnable>> delayed = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Integer>                        types   = new CopyOnWriteArrayList<>();
    private final MpscArrayQueue<Integer>                              cmdQueue;

    private final AtomicInteger drainLoopWip = new AtomicInteger(0);

    public LockFreeTypeExecutor(int commandBufferSize, int taskMaxDelaySize,
                                LimitProvider limitProvider) {
        if (commandBufferSize <= 0) {
            throw new IllegalArgumentException();
        }
        if (taskMaxDelaySize <= 0) {
            throw new IllegalArgumentException();
        }
        this.limitProvider = limitProvider;

        this.cmdQueue = new MpscArrayQueue<>(commandBufferSize);
        this.bufferSize = taskMaxDelaySize;
    }

    @Override
    public void start() {
        // nothing todo
    }

    @Override
    public void stop() {
        // nothing todo
    }

    /**
     * 这里我想了一下, 我们需要确保: 如果任务能放到delayed里就一定能被执行正常(除了stop调用外), 如果放不进去(队列满)就应该立即抛异常
     *
     * @param type      type
     * @param asyncTask asyncTask
     */
    @Override
    protected void execute(int type, Runnable asyncTask) {
        MpscArrayQueue<Runnable> q = delayed.get(type);
        if (q == null) {
            q = new MpscArrayQueue<>(bufferSize);
            MpscArrayQueue<Runnable> oldQ = delayed.putIfAbsent(type, q);
            if (oldQ == null) {
                // TODO 这里原子性问题
                types.add(type);
            } else {
                q = oldQ;
            }
        }
        if (!q.offer(asyncTask)) {
            throw new IllegalStateException("task queue full");
        }
        delayedCount.incrementAndGet();

        // TODO 理论上这里也可能爆掉... 但可能性比较小, 因为处理比较快, 此时有可能导致Executor进入不一致的状态(还有数据delay, 但却不继续往下走)
        if (!cmdQueue.offer(type)) {
            throw new IllegalStateException("cmdQueue is full");
        }

        drainLoop();
    }

    @Override
    protected boolean tryExecute(int type, Runnable safeAsyncTask) {
        MpscArrayQueue<Runnable> q = delayed.get(type);
        if (q == null) {
            q = new MpscArrayQueue<>(bufferSize);
            MpscArrayQueue<Runnable> oldQ = delayed.putIfAbsent(type, q);
            if (oldQ == null) {
                // TODO 这里原子性问题
                types.add(type);
            } else {
                q = oldQ;
            }
        }
        if (!q.offer(safeAsyncTask)) {
            return false;
        }
        delayedCount.incrementAndGet();

        // TODO 理论上这里也可能爆掉... 但可能性比较小, 因为处理比较快, 此时有可能导致Executor进入不一致的状态(还有数据delay, 但却不继续往下走)
        if (!cmdQueue.offer(type)) {
            throw new IllegalStateException("cmdQueue is full");
        }

        drainLoop();
        return true;
    }

    private boolean canExecute() {
        // <=0 means no limit
        return (maxConcurrency <= 0 || wip.get() < maxConcurrency)
                && (maxBatch <= 0 || executed < maxBatch || !otherTypeHasDelayed);
    }

    private void drainLoop() {
        if (drainLoopWip.getAndIncrement() != 0) {
            return;
        }
        int delta = drainLoopWip.get();
        do {
            for (; ; ) {
                // type0 新增了一个task
                Integer type0 = cmdQueue.relaxedPoll();
                if (type0 == null) {
                    // 没有任务了 break
                    break;
                }
                int type = type0;
                MpscArrayQueue<Runnable> q = delayed.get(type0);
                if (executingType == 0) {
                    executingType = type;
                    maxBatch = limitProvider.getMaxBatch(type);
                    maxConcurrency = limitProvider.getMaxConcurrency(type);
                    drainQueue(q);
                } else if (executingType == type) {
                    drainQueue(q);
                } else {
                    otherTypeHasDelayed = true;
                }
            }
            if (executingType > 0) {
                MpscArrayQueue<Runnable> q = delayed.get(executingType);
                if (wip.get() == 0 && (q.isEmpty() || !canExecute())) {
                    // 当前queue已经空了 或者超过maxBatch限制, 切换到下个queue去执行
                    switchToOtherType();
                } else {
                    // 否则可能还有继续执行的余地
                    drainQueue(q);
                }
            }
            delta = drainLoopWip.addAndGet(-delta);
        } while (delta != 0);
    }

    private void switchToOtherType() {
        executingType = 0;
        executed = 0;
        otherTypeHasDelayed = false;
        // 这里也不对 有并发问题...
        if (delayedCount.get() == 0) {
            return;
        }
        int size = types.size();
        for (int i = 0; i < size; ++i) {
            if (++typeIndex == size) {
                typeIndex = 0;
            }
            int type = types.get(typeIndex);
            MpscArrayQueue<Runnable> q = delayed.get(type);
            if (!q.isEmpty()) {
                executingType = type;
                int typeIndex2 = typeIndex;
                for (int j = i + 1; j < size; ++j) {
                    if (++typeIndex2 == size) {
                        typeIndex2 = 0;
                    }
                    MpscArrayQueue<Runnable> q2 = delayed.get(types.get(typeIndex2));
                    if (!q2.isEmpty()) {
                        otherTypeHasDelayed = true;
                        break;
                    }
                }
                maxConcurrency = limitProvider.getMaxConcurrency(type);
                maxBatch = limitProvider.getMaxBatch(type);
                drainQueue(q);
                return;
            }
        }
    }

    private void drainQueue(MpscArrayQueue<Runnable> q) {
        for (; canExecute(); ) {
            Runnable task = q.relaxedPoll();
            if (task == null) {
                break;
            }
            wip.incrementAndGet();
            ++executed;
            delayedCount.decrementAndGet();
            safeExecute(task);
        }
    }

    @Override
    protected void ack(int type) {
        wip.decrementAndGet();
        drainLoop();
    }

    @Override
    public Stat stat() {
        Stat stat = new Stat();
        stat.setDelayedSize(delayedCount.get());
        stat.setWip(wip.get());
        return stat;
    }
}
