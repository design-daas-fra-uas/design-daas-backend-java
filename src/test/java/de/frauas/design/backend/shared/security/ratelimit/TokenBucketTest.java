package de.frauas.design.backend.shared.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TokenBucket")
class TokenBucketTest {

    private static final long MINUTE = 60_000L;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private TokenBucket bucket(int perMinute) {
        return new TokenBucket(perMinute, MINUTE, now::get);
    }

    @Test
    @DisplayName("starts full and rejects once all tokens are used")
    void startsFull_thenRejects() {
        TokenBucket bucket = bucket(3);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("refills one token per period / capacity")
    void refillsAtConfiguredRate() {
        TokenBucket bucket = bucket(60); // one token per second
        for (int i = 0; i < 60; i++) {
            bucket.tryConsume();
        }
        assertThat(bucket.tryConsume()).isFalse();

        now.addAndGet(999);
        assertThat(bucket.tryConsume()).isFalse();

        now.addAndGet(1);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("keeps fractional tokens, so steady traffic gets the full rate")
    void keepsFractions() {
        TokenBucket bucket = bucket(60);
        for (int i = 0; i < 60; i++) {
            bucket.tryConsume();
        }
        // 2 x 500 ms add up to one whole token
        now.addAndGet(500);
        assertThat(bucket.tryConsume()).isFalse();
        now.addAndGet(500);
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    @DisplayName("never holds more than its capacity")
    void capsAtCapacity() {
        TokenBucket bucket = bucket(2);
        now.addAndGet(10 * MINUTE);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("isFull is true for a new bucket and again after a full refill")
    void isFull() {
        TokenBucket bucket = bucket(2);
        assertThat(bucket.isFull()).isTrue();

        bucket.tryConsume();
        assertThat(bucket.isFull()).isFalse();

        now.addAndGet(MINUTE);
        assertThat(bucket.isFull()).isTrue();
    }

    @Test
    @DisplayName("a clock going backwards does not add or remove tokens")
    void clockBackwards() {
        TokenBucket bucket = bucket(1);
        bucket.tryConsume();
        now.addAndGet(-MINUTE);

        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("rejects a non-positive rate or period")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> new TokenBucket(0, MINUTE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the system-clock constructor starts full")
    void systemClock() {
        assertThat(new TokenBucket(1, MINUTE).tryConsume()).isTrue();
    }
}
