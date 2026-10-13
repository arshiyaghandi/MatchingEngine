package com.example.matchingengine.api;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.dto.*;
import com.example.matchingengine.service.IdGeneratorService;
import com.example.matchingengine.service.MatchingEngineService;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * WebFlux functional handler for the Matching Engine REST API.
 */
@Component
public class MatchingEngineHandler {

    private final MatchingEngineService service;
    private final IdGeneratorService idGeneratorService;
    private final Validator validator;

    public MatchingEngineHandler(MatchingEngineService service,
                                 IdGeneratorService idGeneratorService,
                                 Validator validator) {
        this.service = service;
        this.idGeneratorService = idGeneratorService;
        this.validator = validator;
    }

    /**
     * {@code POST /api/orders} — Submit a new order.
     */
    public Mono<ServerResponse> submitOrder(ServerRequest request) {
        return request.bodyToMono(OrderRequest.class)
                .doOnNext(this::validateDto)
                .flatMap(req -> {
                    OrderType type = OrderType.valueOf(req.type().toUpperCase());
                    Side side = Side.valueOf(req.side().toUpperCase());

                    Order order = new Order(
                            idGeneratorService.nextId(),
                            req.symbol(),
                            side,
                            type,
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
                .onErrorResume(ServerWebInputException.class,
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getReason() != null ? e.getReason() : e.getMessage())));
    }

    /**
     * {@code DELETE /api/orders} — Cancel an order.
     */
    public Mono<ServerResponse> cancelOrder(ServerRequest request) {
        return request.bodyToMono(CancelRequest.class)
                .doOnNext(this::validateDto)
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
        String symbol = request.pathVariable("symbol").toUpperCase();
        OrderBookSnapshot snapshot = service.getOrderBookSnapshot(symbol);
        return ServerResponse.ok().bodyValue(snapshot);
    }

    /**
     * {@code GET /api/trades/{symbol}} — Get recent trades.
     */
    public Mono<ServerResponse> getTrades(ServerRequest request) {
        String symbol = request.pathVariable("symbol").toUpperCase();
        List<TradeResponse> responses = service.getTrades(symbol).stream()
                .limit(50)
                .map(t -> new TradeResponse(
                        t.getTradeId(), t.getBuyOrderId(),
                        t.getSellOrderId(), t.getSymbol(),
                        t.getPrice(), t.getQuantity()))
                .toList();

        return ServerResponse.ok().bodyValue(responses);
    }

    private <T> void validateDto(T target) {
        Errors errors = new BeanPropertyBindingResult(target, target.getClass().getName());
        validator.validate(target, errors);
        if (errors.hasErrors()) {
            String msg = errors.getAllErrors().getFirst().getDefaultMessage();
            throw new IllegalArgumentException(msg != null ? msg : "Validation error");
        }
    }
}
