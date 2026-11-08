package de.frauas.design.backend.shared.security.ratelimit;

import java.util.function.LongSupplier;

/**
 * Thread-safe token bucket for per-client rate limiting.
 *
 * <p>The bucket starts full with {@code capacity} tokens and refills continuously at
 * {@code capacity} tokens per refill period. A request consumes one token; without a
 * whole token left it is rejected. Fractions of a token are kept, so the configured
 * rate holds exactly, also under steady traffic.</p>
 *
 * <p>The state is two fields guarded by the bucket's monitor: simple and fast enough
 * for the small request rates of the login and registration endpoints.</p>
 */
final class TokenBucket {

    private final double capacity;
    private final double refillTokensPerMillisecond;
    private final LongSupplier clock;

    private double tokens;
    private long lastRefillMillis;

    TokenBucket(int requestsPerMinute, long refillPeriodMillis) {
        this(requestsPerMinute, refillPeriodMillis, System::currentTimeMillis);
    }

    TokenBucket(int requestsPerMinute, long refillPeriodMillis, LongSupplier clock) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requestsPerMinute must be greater than zero");
        }
        if (refillPeriodMillis <= 0) {
            throw new IllegalArgumentException("refillPeriodMillis must be greater than zero");
        }
        this.capacity = requestsPerMinute;
        this.refillTokensPerMillisecond = (double) requestsPerMinute / refillPeriodMillis;
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefillMillis = clock.getAsLong();
    }

    /**
     * Takes one token if available.
     *
     * @return {@code true} if the request is allowed
     */
    synchronized boolean tryConsume() {
        refill();
        if (tokens < 1) {
            return false;
        }
        tokens -= 1;
        return true;
    }

    /**
     * A full bucket behaves exactly like a new one, so it can be dropped to free memory.
     *
     * @return {@code true} if the bucket has refilled completely
     */
    synchronized boolean isFull() {
        refill();
        return tokens >= capacity;
    }

    private void refill() {
        long now = clock.getAsLong();
        long elapsed = Math.max(0, now - lastRefillMillis);
        tokens = Math.min(capacity, tokens + elapsed * refillTokensPerMillisecond);
        lastRefillMillis = now;
    }
}
