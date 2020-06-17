package com.xzchaoo.asyncexecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiangfeng.xzc
 * @date 2020-06-11
 */
public abstract class AbstractTypeExecutor implements TypeExecutor {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected void safeExecute(Runnable asyncTask) {
        try {
            asyncTask.run();
        } catch (Exception e) {
            // TODO 强调一下不应该抛异常
            logger.error("asyncError error", e);
        }
    }

    @Override
    public Stat stat() {
        return null;
    }
}
