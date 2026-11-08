package de.frauas.design.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("AsyncConfig")
class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    @DisplayName("uses a small bounded pool")
    void boundedPool() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getAsyncExecutor();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("async-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("drops tasks with a warning instead of blocking when the queue is full")
    void rejectsWithoutThrowing() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getAsyncExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            // 4 running + 100 queued fill the pool; the next ones are rejected
            for (int i = 0; i < 110; i++) {
                assertThatCode(() -> executor.execute(() -> {
                            try {
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .doesNotThrowAnyException();
            }
            assertThat(executor.getQueueSize()).isEqualTo(100);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("logs failures of @Async methods")
    void uncaughtHandler() throws Exception {
        assertThatCode(() -> config.getAsyncUncaughtExceptionHandler()
                        .handleUncaughtException(
                                new IllegalStateException("boom"), Object.class.getMethod("toString")))
                .doesNotThrowAnyException();
    }
}
