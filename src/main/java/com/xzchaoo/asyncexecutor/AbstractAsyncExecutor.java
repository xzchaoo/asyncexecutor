package com.xzchaoo.asyncexecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-09
 */
public abstract class AbstractAsyncExecutor implements AsyncExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAsyncExecutor.class);

    protected void execute(Runnable asyncCommand) {
        // 这个方法一定不能阻塞 否则出大事!
        try {
            asyncCommand.run();
        } catch (Exception e) {
            // 按约定 不应该抛异常, 我们只能记录error然后忽略
            LOGGER.error("uncaught exception", e);
        }
    }
}
