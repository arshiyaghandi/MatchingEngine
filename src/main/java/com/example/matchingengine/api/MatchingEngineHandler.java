package com.example.matchingengine.api;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.PriceLevel;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.dto.*;
import com.example.matchingengine.dto.OrderBookSnapshot.PriceLevelEntry;
import com.example.matchingengine.engine.OrderBook;
import com.example.matchingengine.service.MatchingEngineService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebFlux functional handler for the Matching Engine REST API.
 */
@Component
public class MatchingEngineHandler {

    private final MatchingEngineService service;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);

    public MatchingEngineHandler(MatchingEngineService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/orders} — Submit a new order.
     */
    public Mono<ServerResponse> submitOrder(ServerRequest request) {
        return request.bodyToMono(OrderRequest.class)
                .flatMap(req -> {
                    Order order = new Order(
                            orderIdGenerator.getAndIncrement(),
                            req.symbol(),
                            Side.valueOf(req.side().toUpperCase()),
                            OrderType.valueOf(req.type().toUpperCase()),
                            req.price(),
                            req.quantity(),
                            System.nanoTime());

                    return service.submitOrder(order)
                            .map(trades -> new OrderResponse(
                                    order.getId(),
                                    order.getSymbol(),
                                    order.getSide().name(),
                                    order.getPrice(),
                                    order.getOriginalQuantity(),
                                    trades.stream()
                                            .map(t -> new TradeResponse(
                                                    t.getTradeId(), t.getBuyOrderId(),
                                                    t.getSellOrderId(), t.getSymbol(),
                                                    t.getPrice(), t.getQuantity()))
                                            .toList()));
                })
                .flatMap(resp -> ServerResponse.ok().bodyValue(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())))
                .onErrorResume(Exception.class,
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())));
    }

    /**
     * {@code DELETE /api/orders} — Cancel an order.
     */
    public Mono<ServerResponse> cancelOrder(ServerRequest request) {
        return request.bodyToMono(CancelRequest.class)
                .flatMap(req -> service.cancelOrder(
                        req.symbol(),
                        req.orderId(),
                        Side.valueOf(req.side().toUpperCase())))
                .flatMap(cancelled -> {
                    if (cancelled) {
                        return ServerResponse.ok().bodyValue(Map.of("status", "CANCELLED"));
                    }
                    return ServerResponse.status(404).bodyValue(Map.of("error", "Order not found"));
                });
    }

    /**
     * {@code GET /api/orderbook/{symbol}} — Get order-book snapshot.
     */
    public Mono<ServerResponse> getOrderBook(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        OrderBook book = service.getEngine().getOrderBook(symbol);

        if (book == null) {
            return ServerResponse.ok().bodyValue(
                    new OrderBookSnapshot(symbol, List.of(), List.of()));
        }

        synchronized (book) {
            List<PriceLevelEntry> bids = aggregateSide(book.getBids());
            List<PriceLevelEntry> asks = aggregateSide(book.getAsks());
            return ServerResponse.ok().bodyValue(
                    new OrderBookSnapshot(symbol, bids, asks));
        }
    }

    private List<PriceLevelEntry> aggregateSide(TreeMap<Long, PriceLevel> side) {
        List<PriceLevelEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, PriceLevel> e : side.entrySet()) {
            long totalQty = 0;
            int count = 0;
            for (var order : e.getValue().getOrders()) {
                totalQty += order.getRemainingQuantity();
                count++;
            }
            entries.add(new PriceLevelEntry(e.getKey(), totalQty, count));
        }
        return entries;
    }
}
