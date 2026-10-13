package com.example.matchingengine.service;

import com.example.matchingengine.cache.OrderBookCacheService;
import com.example.matchingengine.cache.OrderEventLogService;
import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderStatus;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.dto.OrderBookSnapshot;
import com.example.matchingengine.dto.TradeResponse;
import com.example.matchingengine.engine.MatchingEngine;
import com.example.matchingengine.engine.OrderBook;
import com.example.matchingengine.kafka.TradeKafkaProducer;
import com.example.matchingengine.persistence.PersistenceService;
import com.example.matchingengine.util.OrderBookAggregator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 * Senior Reactive service layer wrapping the pure-Java {@link MatchingEngine}.
 *
 * <p>Orchestrates:
 * <ol>
 *   <li>Matching (synchronous offload on {@code boundedElastic} scheduler)</li>
 *   <li>Persistence → PostgreSQL (R2DBC)</li>
 *   <li>Caching → Redis (order-book snapshot, write-through)</li>
 *   <li>Publishing → Kafka (trade events)</li>
 *   <li>Event logging → Redis (daily event log with TTL)</li>
 *   <li>Real-time broadcasting → WebSockets via {@link EventBroadcastService}</li>
 *   <li>Micrometer metric recording</li>
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
    private static final int MAX_TRADE_HISTORY = 500;

    private final MatchingEngine engine;
    private final Map<String, List<Trade>> tradeHistory = new ConcurrentHashMap<>();

    private final PersistenceService persistenceService;
    private final OrderBookCacheService cacheService;
    private final TradeKafkaProducer tradeProducer;
    private final OrderEventLogService eventLogService;
    private final EventBroadcastService broadcastService;

    // Micrometer metrics
    private final Counter submittedOrdersCounter;
    private final Counter executedTradesCounter;
    private final Timer orderMatchTimer;

    public MatchingEngineService(
            @Autowired(required = false) PersistenceService persistenceService,
            @Autowired(required = false) OrderBookCacheService cacheService,
            @Autowired(required = false) TradeKafkaProducer tradeProducer,
            @Autowired(required = false) OrderEventLogService eventLogService,
            @Autowired(required = false) EventBroadcastService broadcastService,
            @Autowired(required = false) IdGeneratorService idGeneratorService,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.engine = new MatchingEngine(idGeneratorService != null ? idGeneratorService::nextId : null);
        this.persistenceService = persistenceService;
        this.cacheService = cacheService;
        this.tradeProducer = tradeProducer;
        this.eventLogService = eventLogService;
        this.broadcastService = broadcastService;

        if (meterRegistry != null) {
            this.submittedOrdersCounter = meterRegistry.counter("matching_engine.orders.submitted");
            this.executedTradesCounter = meterRegistry.counter("matching_engine.trades.executed");
            this.orderMatchTimer = meterRegistry.timer("matching_engine.order.match.duration");
        } else {
            this.submittedOrdersCounter = null;
            this.executedTradesCounter = null;
            this.orderMatchTimer = null;
        }
    }

    // ────────────────────────── Public API ──────────────────────────

    /**
     * Submits an order reactively.
     *
     * <p>Matching runs on {@link Schedulers#boundedElastic()} to avoid blocking
     * Netty event-loop threads. Side effects execute concurrently via {@link Mono#when}
     * and are individually fault-tolerant.</p>
     */
    public Mono<List<Trade>> submitOrder(Order order) {
        if (submittedOrdersCounter != null) {
            submittedOrdersCounter.increment();
        }

        return Mono.fromCallable(() -> {
            if (orderMatchTimer != null) {
                return orderMatchTimer.recordCallable(() -> engine.submitOrder(order));
            }
            return engine.submitOrder(order);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(trades -> {
            if (!trades.isEmpty()) {
                if (executedTradesCounter != null) {
                    executedTradesCounter.increment(trades.size());
                }
                appendTradeHistory(order.getSymbol(), trades);
            }

            // Real-time broadcast
            broadcastUpdates(order.getSymbol(), trades);

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
                        broadcastOrderBookUpdate(symbol);
                        return processCancelSideEffects(orderId, symbol)
                                .thenReturn(true);
                    }
                    return Mono.just(false);
                });
    }

    /** Returns the underlying engine for inspection. */
    public MatchingEngine getEngine() {
        return engine;
    }

    /**
     * Safely takes an order-book snapshot without leaking raw internal collections.
     */
    public OrderBookSnapshot getOrderBookSnapshot(String symbol) {
        OrderBook book = engine.getOrderBook(symbol);
        if (book == null) {
            return new OrderBookSnapshot(symbol, List.of(), List.of());
        }
        synchronized (book) {
            return OrderBookAggregator.buildSnapshot(symbol, book);
        }
    }

    // ────────────────────────── Helpers ──────────────────────────

    private void appendTradeHistory(String symbol, List<Trade> trades) {
        tradeHistory.compute(symbol, (k, existing) -> {
            List<Trade> list = (existing != null) ? existing : Collections.synchronizedList(new ArrayList<>());
            list.addAll(0, trades);
            while (list.size() > MAX_TRADE_HISTORY) {
                list.remove(list.size() - 1);
            }
            return list;
        });
    }

    private void broadcastUpdates(String symbol, List<Trade> trades) {
        if (broadcastService != null) {
            broadcastOrderBookUpdate(symbol);
            if (!trades.isEmpty()) {
                List<TradeResponse> tradeResponses = trades.stream()
                        .map(t -> new TradeResponse(
                                t.getTradeId(), t.getBuyOrderId(),
                                t.getSellOrderId(), t.getSymbol(),
                                t.getPrice(), t.getQuantity()))
                        .toList();
                broadcastService.publishTrades(tradeResponses);
            }
        }
    }

    private void broadcastOrderBookUpdate(String symbol) {
        if (broadcastService != null) {
            OrderBookSnapshot snapshot = getOrderBookSnapshot(symbol);
            broadcastService.publishOrderBookUpdate(snapshot);
        }
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

                // Update resting orders fill status
                for (Trade trade : trades) {
                    long restingOrderId = (order.getSide() == Side.BUY) ? trade.getSellOrderId() : trade.getBuyOrderId();
                    OrderBook book = engine.getOrderBook(order.getSymbol());
                    long remainingQty = 0;
                    if (book != null && book.containsOrder(restingOrderId)) {
                        Order restingOrder = (order.getSide() == Side.BUY)
                                ? book.getAsks().values().stream().flatMap(pl -> pl.getOrders().stream()).filter(o -> o.getId() == restingOrderId).findFirst().orElse(null)
                                : book.getBids().values().stream().flatMap(pl -> pl.getOrders().stream()).filter(o -> o.getId() == restingOrderId).findFirst().orElse(null);
                        if (restingOrder != null) {
                            remainingQty = restingOrder.getRemainingQuantity();
                        }
                    }
                    effects.add(persistenceService.updateOrderFill(restingOrderId, remainingQty)
                            .onErrorResume(e -> {
                                log.warn("persist resting order fill update failed [{}]: {}", restingOrderId, e.getMessage());
                                return Mono.empty();
                            }));
                }
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
            effects.add(persistenceService.updateOrderStatus(orderId, OrderStatus.CANCELLED)
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
