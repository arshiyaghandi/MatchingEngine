package com.example.matchingengine.service;

import com.example.matchingengine.cache.OrderBookCacheService;
import com.example.matchingengine.cache.OrderEventLogService;
import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.engine.MatchingEngine;
import com.example.matchingengine.engine.OrderBook;
import com.example.matchingengine.kafka.TradeKafkaProducer;
import com.example.matchingengine.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive service layer wrapping the pure-Java {@link MatchingEngine}.
 *
 * <p>Orchestrates:
 * <ol>
 *   <li>Matching (synchronous, on {@code boundedElastic} scheduler)</li>
 *   <li>Persistence → PostgreSQL (R2DBC)</li>
 *   <li>Caching → Redis (order-book snapshot, write-through)</li>
 *   <li>Publishing → Kafka (trade events)</li>
 *   <li>Event logging → Redis (daily event log with TTL)</li>
 * </ol>
 *
 * <p>All infrastructure dependencies are <strong>optional</strong>.
 * If a dependency is absent the engine still works correctly in-memory.</p>
 *
 * <p>Side effects are fault-tolerant — a downstream failure (DB / Redis / Kafka)
 * never prevents the match result from being returned to the caller.</p>
 */
@Service
public class MatchingEngineService {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineService.class);

    private final MatchingEngine engine;
    private final Map<String, List<Trade>> tradeHistory = new ConcurrentHashMap<>();

    private final PersistenceService persistenceService;
    private final OrderBookCacheService cacheService;
    private final TradeKafkaProducer tradeProducer;
    private final OrderEventLogService eventLogService;

    public MatchingEngineService(
            @Autowired(required = false) PersistenceService persistenceService,
            @Autowired(required = false) OrderBookCacheService cacheService,
            @Autowired(required = false) TradeKafkaProducer tradeProducer,
            @Autowired(required = false) OrderEventLogService eventLogService) {
        this.engine = new MatchingEngine();
        this.persistenceService = persistenceService;
        this.cacheService = cacheService;
        this.tradeProducer = tradeProducer;
        this.eventLogService = eventLogService;
    }

    // ────────────────────────── Public API ──────────────────────────

    /**
     * Submits an order reactively.
     *
     * <p>Matching runs on {@link Schedulers#boundedElastic()} to avoid blocking
     * Netty event-loop threads. Side effects (persist / cache / publish) execute
     * concurrently via {@link Mono#when} and are individually fault-tolerant.</p>
     */
    public Mono<List<Trade>> submitOrder(Order order) {
        return Mono.fromCallable(() -> engine.submitOrder(order))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(trades -> {
                    if (!trades.isEmpty()) {
                        tradeHistory.computeIfAbsent(order.getSymbol(), k -> Collections.synchronizedList(new ArrayList<>()))
                                .addAll(0, trades); // Prepend latest trades
                    }
                    return processSubmitSideEffects(order, trades)
                            .thenReturn(trades);
                });
    }

    /** Gets recent trades for a symbol (for UI). */
    public List<Trade> getTrades(String symbol) {
        return tradeHistory.getOrDefault(symbol, List.of());
    }

    /** Cancels an order reactively. */
    public Mono<Boolean> cancelOrder(String symbol, long orderId, Side side) {
        return Mono.fromCallable(() -> engine.cancelOrder(symbol, orderId, side))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(cancelled -> {
                    if (cancelled) {
                        return processCancelSideEffects(orderId, symbol)
                                .thenReturn(true);
                    }
                    return Mono.just(false);
                });
    }

    /** Returns the underlying (non-reactive) engine for inspection. */
    public MatchingEngine getEngine() {
        return engine;
    }

    // ────────────────────────── Side Effects ──────────────────────────

    private Mono<Void> processSubmitSideEffects(Order order, List<Trade> trades) {
        List<Mono<Void>> effects = new ArrayList<>();

        if (persistenceService != null) {
            effects.add(persistenceService.persistOrder(order)
                    .onErrorResume(e -> {
                        log.warn("persist-order failed [{}]: {}", order.getId(), e.getMessage());
                        return Mono.empty();
                    }));
            if (!trades.isEmpty()) {
                effects.add(persistenceService.persistTrades(trades)
                        .onErrorResume(e -> {
                            log.warn("persist-trades failed: {}", e.getMessage());
                            return Mono.empty();
                        }));
            }
        }

        if (cacheService != null) {
            OrderBook book = engine.getOrderBook(order.getSymbol());
            if (book != null) {
                effects.add(cacheService.cacheOrderBookSnapshot(order.getSymbol(), book)
                        .onErrorResume(e -> {
                            log.warn("cache-snapshot failed [{}]: {}", order.getSymbol(), e.getMessage());
                            return Mono.empty();
                        }));
            }
        }

        if (tradeProducer != null && !trades.isEmpty()) {
            effects.add(tradeProducer.publishTrades(trades)
                    .onErrorResume(e -> {
                        log.warn("kafka-publish failed: {}", e.getMessage());
                        return Mono.empty();
                    }));
        }

        if (eventLogService != null) {
            effects.add(eventLogService.logOrderEvent(order, trades)
                    .onErrorResume(e -> {
                        log.warn("event-log failed: {}", e.getMessage());
                        return Mono.empty();
                    }));
        }

        return effects.isEmpty() ? Mono.empty() : Mono.when(effects);
    }

    private Mono<Void> processCancelSideEffects(long orderId, String symbol) {
        List<Mono<Void>> effects = new ArrayList<>();

        if (persistenceService != null) {
            effects.add(persistenceService.updateOrderStatus(orderId, "CANCELLED")
                    .onErrorResume(e -> {
                        log.warn("update-cancel-status failed [{}]: {}", orderId, e.getMessage());
                        return Mono.empty();
                    }));
        }

        if (cacheService != null) {
            OrderBook book = engine.getOrderBook(symbol);
            if (book != null) {
                effects.add(cacheService.cacheOrderBookSnapshot(symbol, book)
                        .onErrorResume(e -> {
                            log.warn("cache-update-after-cancel failed [{}]: {}", symbol, e.getMessage());
                            return Mono.empty();
                        }));
            }
        }

        if (eventLogService != null) {
            effects.add(eventLogService.logCancelEvent(orderId, symbol)
                    .onErrorResume(e -> {
                        log.warn("event-log-cancel failed [{}]: {}", orderId, e.getMessage());
                        return Mono.empty();
                    }));
        }

        return effects.isEmpty() ? Mono.empty() : Mono.when(effects);
    }
}
