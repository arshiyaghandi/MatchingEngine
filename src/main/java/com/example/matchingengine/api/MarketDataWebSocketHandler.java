package com.example.matchingengine.api;

import com.example.matchingengine.service.EventBroadcastService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebSocket handler that streams real-time market data to connected clients.
 *
 * <h3>Connection URL</h3>
 * <pre>ws://host:port/ws/market?symbol=AAPL</pre>
 *
 * <h3>Outbound Message Format</h3>
 * <pre>
 * // Order-book update
 * { "type": "orderbook", "data": { "symbol": "AAPL", "bids": [...], "asks": [...] } }
 *
 * // Trade event
 * { "type": "trade", "data": { "tradeId": 1, "symbol": "AAPL", ... } }
 * </pre>
 *
 * <p>Both streams are multiplexed over a single WebSocket connection.
 * Backpressure is handled by Reactor's buffer strategy.</p>
 */
@Component
public class MarketDataWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    private final EventBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    public MarketDataWebSocketHandler(EventBroadcastService broadcastService,
                                       ObjectMapper objectMapper) {
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String symbol = extractSymbol(session);
        log.info("WebSocket connected: sessionId={} symbol={}", session.getId(), symbol);

        // Merge order-book snapshot stream and trade stream
        Flux<String> orderBookMessages = broadcastService.orderBookStream(symbol)
                .map(snap -> toJson(Map.of("type", "orderbook", "data", snap)));

        Flux<String> tradeMessages = broadcastService.tradeStream(symbol)
                .map(trade -> toJson(Map.of("type", "trade", "data", trade)));

        Flux<WebSocketMessage> outbound = Flux.merge(orderBookMessages, tradeMessages)
                .map(session::textMessage);

        // Send outbound messages; keep alive by consuming (ignoring) inbound
        return session.send(outbound)
                .and(session.receive()
                        .doFinally(sig -> log.info("WebSocket disconnected: sessionId={} signal={}",
                                session.getId(), sig))
                        .then());
    }

    /**
     * Extracts the {@code symbol} query parameter from the WebSocket URI.
     * Falls back to {@code "AAPL"} if not provided.
     */
    private String extractSymbol(WebSocketSession session) {
        if (session.getHandshakeInfo().getUri().getQuery() == null) {
            return "AAPL";
        }
        var params = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams();
        String symbol = params.getFirst("symbol");
        return (symbol != null && !symbol.isBlank()) ? symbol.toUpperCase() : "AAPL";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON serialisation failed", e);
            return "{\"type\":\"error\",\"data\":\"serialisation_failed\"}";
        }
    }
}
