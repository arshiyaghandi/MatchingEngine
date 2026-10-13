package com.example.matchingengine.service;

import com.example.matchingengine.dto.OrderBookSnapshot;
import com.example.matchingengine.dto.TradeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * In-process reactive event bus backed by Project Reactor {@link Sinks}.
 *
 * <p>Publishes two independent streams:
 * <ol>
 *   <li><strong>Order-book snapshots</strong> — emitted after every order
 *       submission or cancellation.</li>
 *   <li><strong>Trade events</strong> — emitted for each executed trade.</li>
 * </ol>
 *
 * <p>Consumers (e.g. WebSocket handlers) subscribe to these streams and
 * receive updates with natural backpressure handling.</p>
 *
 * <p>Buffer size of 256 provides headroom for burst scenarios without
 * unbounded memory growth.</p>
 */
@Service
public class EventBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcastService.class);

    private final Sinks.Many<OrderBookSnapshot> orderBookSink =
            Sinks.many().multicast().onBackpressureBuffer(256);

    private final Sinks.Many<TradeResponse> tradeSink =
            Sinks.many().multicast().onBackpressureBuffer(256);

    // ────────────────────────── Publishers ──────────────────────────

    /**
     * Publishes an updated order-book snapshot to all subscribers.
     */
    public void publishOrderBookUpdate(OrderBookSnapshot snapshot) {
        Sinks.EmitResult result = orderBookSink.tryEmitNext(snapshot);
        if (result.isFailure()) {
            log.warn("Failed to emit order-book snapshot for {}: {}", snapshot.symbol(), result);
        }
    }

    /**
     * Publishes trade events to all subscribers.
     */
    public void publishTrades(List<TradeResponse> trades) {
        for (TradeResponse trade : trades) {
            Sinks.EmitResult result = tradeSink.tryEmitNext(trade);
            if (result.isFailure()) {
                log.warn("Failed to emit trade {}: {}", trade.tradeId(), result);
            }
        }
    }

    // ────────────────────────── Subscribers ──────────────────────────

    /**
     * Returns a hot {@link Flux} of order-book snapshots for a specific symbol.
     */
    public Flux<OrderBookSnapshot> orderBookStream(String symbol) {
        return orderBookSink.asFlux()
                .filter(snap -> snap.symbol().equals(symbol));
    }

    /**
     * Returns a hot {@link Flux} of all order-book snapshots (all symbols).
     */
    public Flux<OrderBookSnapshot> orderBookStream() {
        return orderBookSink.asFlux();
    }

    /**
     * Returns a hot {@link Flux} of trade events for a specific symbol.
     */
    public Flux<TradeResponse> tradeStream(String symbol) {
        return tradeSink.asFlux()
                .filter(t -> t.symbol().equals(symbol));
    }

    /**
     * Returns a hot {@link Flux} of all trade events (all symbols).
     */
    public Flux<TradeResponse> tradeStream() {
        return tradeSink.asFlux();
    }
}
