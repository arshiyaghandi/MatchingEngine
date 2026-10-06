package com.example.matchingengine.cache;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Trade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Daily event logger backed by Redis.
 *
 * <p>Key pattern: {@code events:orders:{yyyy-MM-dd}}<br>
 * Each key is a Redis List of JSON event entries, auto-expiring after
 * {@code matching-engine.redis.event-log-ttl-days} days.</p>
 *
 * <p>Final/settled state is persisted in PostgreSQL; Redis holds the
 * operational event log for quick intra-day inspection.</p>
 */
@Service
@ConditionalOnProperty(name = "matching-engine.redis.enabled", havingValue = "true", matchIfMissing = false)
public class OrderEventLogService {

    private static final String KEY_PREFIX = "events:orders:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public OrderEventLogService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            @Value("${matching-engine.redis.event-log-ttl-days:7}") int ttlDays) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.ttl = Duration.ofDays(ttlDays);
    }

    /**
     * Logs an order submission event (with any resulting trades).
     */
    public Mono<Void> logOrderEvent(Order order, List<Trade> trades) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("action", trades.isEmpty() ? "SUBMITTED" : (order.isFilled() ? "FILLED" : "PARTIALLY_FILLED"));
        event.put("orderId", order.getId());
        event.put("symbol", order.getSymbol());
        event.put("side", order.getSide().name());
        event.put("price", order.getPrice());
        event.put("quantity", order.getOriginalQuantity());
        event.put("remainingQuantity", order.getRemainingQuantity());
        event.put("timestamp", System.currentTimeMillis());

        if (!trades.isEmpty()) {
            ArrayNode tradesArr = event.putArray("trades");
            for (Trade t : trades) {
                ObjectNode tn = tradesArr.addObject();
                tn.put("tradeId", t.getTradeId());
                tn.put("price", t.getPrice());
                tn.put("quantity", t.getQuantity());
            }
        }

        return pushEvent(event);
    }

    /**
     * Logs an order cancellation event.
     */
    public Mono<Void> logCancelEvent(long orderId, String symbol) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("action", "CANCELLED");
        event.put("orderId", orderId);
        event.put("symbol", symbol);
        event.put("timestamp", System.currentTimeMillis());

        return pushEvent(event);
    }

    // ──────────────── Helpers ────────────────

    private Mono<Void> pushEvent(ObjectNode event) {
        String key = KEY_PREFIX + LocalDate.now().format(DATE_FMT);
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        return redisTemplate.opsForList().rightPush(key, json)
                .then(redisTemplate.expire(key, ttl))
                .then();
    }
}
