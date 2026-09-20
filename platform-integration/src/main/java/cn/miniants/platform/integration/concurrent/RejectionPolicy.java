package cn.miniants.platform.integration.concurrent;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public enum RejectionPolicy {
    ABORT {
        @Override
        RejectedExecutionHandler handler() {
            return new ThreadPoolExecutor.AbortPolicy();
        }
    },
    CALLER_RUNS {
        @Override
        RejectedExecutionHandler handler() {
            return (task, executor) -> {
                if (executor.isShutdown()) {
                    throw new RejectedExecutionException("批处理执行器已关闭");
                }
                task.run();
            };
        }
    };

    abstract RejectedExecutionHandler handler();
}
