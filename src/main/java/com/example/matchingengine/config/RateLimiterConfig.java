package com.example.matchingengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory token-bucket rate limiter for the REST API.
 *
 * <p>Limits the number of order submissions per IP address within a
 * configurable time window. Designed for single-JVM deployments.</p>
 *
 * <p>For distributed deployments, replace with a Redis-backed rate limiter.</p>
 */
@Configuration
public class RateLimiterConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterConfig.class);

    @Value("${matching-engine.rate-limit.max-requests-per-second:100}")
    private int maxRequestsPerSecond;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Checks if a request from the given client IP is allowed.
     *
     * @param clientIp the client's IP address
     * @return true if the request is within rate limits
     */
    public boolean isAllowed(String clientIp) {
        TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                ip -> new TokenBucket(maxRequestsPerSecond));
        return bucket.tryConsume();
    }

    /**
     * Simple token-bucket implementation.
     * Refills tokens every second up to the maximum capacity.
     */
    private static class TokenBucket {
        private final int maxTokens;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTime;

        TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        boolean tryConsume() {
            refill();
            return tokens.getAndUpdate(t -> t > 0 ? t - 1 : 0) > 0;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long last = lastRefillTime.get();
            long elapsed = now - last;
            if (elapsed >= 1000 && lastRefillTime.compareAndSet(last, now)) {
                tokens.set(maxTokens);
            }
        }
    }
}
