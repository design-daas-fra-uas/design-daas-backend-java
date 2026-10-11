package de.frauas.design.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configures the executor behind {@code @Async} (currently e-mail sending).
 *
 * <p>A small, bounded pool replaces Spring's default executor so a slow or unreachable
 * SMTP server can neither exhaust threads nor grow an unbounded queue. When the queue is
 * full a task is dropped with a warning rather than blocking the request thread. Failures
 * inside {@code @Async void} methods, which have no caller to receive the exception, are
 * logged here.</p>
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler((task, pool) -> log.warn(
                "Async task rejected — queue full (active={}, queued={}); dropping task",
                pool.getActiveCount(),
                pool.getQueue().size()));
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Async method {}#{} failed", method.getDeclaringClass().getSimpleName(), method.getName(), ex);
    }
}
