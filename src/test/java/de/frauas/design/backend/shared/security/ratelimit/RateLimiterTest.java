package de.frauas.design.backend.shared.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiter")
class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    @DisplayName("limits each client and endpoint separately")
    void separateBucketsPerClientAndEndpoint() {
        assertThat(rateLimiter.tryConsume("1.1.1.1", "/a", 1)).isTrue();
        assertThat(rateLimiter.tryConsume("1.1.1.1", "/a", 1)).isFalse();

        assertThat(rateLimiter.tryConsume("1.1.1.1", "/b", 1)).isTrue();
        assertThat(rateLimiter.tryConsume("2.2.2.2", "/a", 1)).isTrue();
        assertThat(rateLimiter.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("evicts only buckets that are full again")
    void evictsFullBuckets() {
        rateLimiter.tryConsume("1.1.1.1", "/a", 1);
        rateLimiter.tryConsume("2.2.2.2", "/a", 1000);

        // the second bucket refills within milliseconds, the first needs a minute
        sleepQuietly(100);
        int removed = rateLimiter.evictFullBuckets();

        assertThat(removed).isEqualTo(1);
        assertThat(rateLimiter.size()).isEqualTo(1);
        assertThat(rateLimiter.tryConsume("1.1.1.1", "/a", 1)).isFalse();
    }

    @Test
    @DisplayName("eviction with nothing to remove returns 0")
    void evictNothing() {
        assertThat(rateLimiter.evictFullBuckets()).isZero();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
