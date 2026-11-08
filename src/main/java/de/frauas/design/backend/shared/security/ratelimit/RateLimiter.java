package de.frauas.design.backend.shared.security.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Coordinates rate limiting for clients and endpoints.
 *
 * <p>Each client/IP and endpoint combination has its own token bucket. Buckets that
 * have refilled completely are removed every few minutes, so the map does not grow
 * with every client that ever called an endpoint.</p>
 */
@Component
@Slf4j
public class RateLimiter {

    private static final long REFILL_PERIOD_MILLIS = 60_000L;

    /**
     * Cache of token-bucket state keyed by "{clientIp}|{servletPath}".
     */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Attempts to consume one token for the given client and endpoint.
     *
     * @param clientId client identifier, normally the resolved IP address
     * @param endpoint protected endpoint
     * @param requestsPerMinute maximum refill rate
     * @return {@code true} if the request is allowed
     */
    public boolean tryConsume(String clientId, String endpoint, int requestsPerMinute) {
        String key = clientId + "|" + endpoint;
        TokenBucket bucket =
                buckets.computeIfAbsent(key, ignored -> new TokenBucket(requestsPerMinute, REFILL_PERIOD_MILLIS));
        return bucket.tryConsume();
    }

    /**
     * Removes the buckets that are full again: they behave exactly like new ones.
     *
     * @return the number of removed buckets
     */
    @Scheduled(fixedDelayString = "${app.rate-limiting.eviction-interval-ms:300000}")
    public int evictFullBuckets() {
        int before = buckets.size();
        buckets.values().removeIf(TokenBucket::isFull);
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("evictFullBuckets — removed {} idle bucket(s), {} left", removed, buckets.size());
        }
        return removed;
    }

    /** Number of buckets currently held (for tests and monitoring). */
    int size() {
        return buckets.size();
    }
}
