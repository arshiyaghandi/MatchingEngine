package com.example.matchingengine.cache;

import com.example.matchingengine.dto.OrderBookSnapshot;
import com.example.matchingengine.engine.OrderBook;
import com.example.matchingengine.util.OrderBookAggregator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Write-through cache for order-book snapshots in Redis.
 *
 * <p>Key pattern: {@code orderbook:{symbol}}</p>
 * <p>Updated after every order submission or cancellation.</p>
 */
@Service
@ConditionalOnProperty(name = "matching-engine.redis.enabled", havingValue = "true", matchIfMissing = false)
public class OrderBookCacheService {

    private static final String KEY_PREFIX = "orderbook:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderBookCacheService(ReactiveRedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Serialises the current state of the order book and stores it in Redis.
     */
    public Mono<Void> cacheOrderBookSnapshot(String symbol, OrderBook book) {
        OrderBookSnapshot snapshot = OrderBookAggregator.buildSnapshot(symbol, book);
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            return redisTemplate.opsForValue()
                    .set(KEY_PREFIX + symbol, json)
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    /** Retrieves a cached snapshot (may be slightly stale). */
    public Mono<OrderBookSnapshot> getCachedSnapshot(String symbol) {
        return redisTemplate.opsForValue()
                .get(KEY_PREFIX + symbol)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, OrderBookSnapshot.class));
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                });
    }
}
