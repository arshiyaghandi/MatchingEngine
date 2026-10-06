package com.example.matchingengine.persistence;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.Trade;
import com.example.matchingengine.persistence.entity.OrderEntity;
import com.example.matchingengine.persistence.entity.TradeEntity;
import com.example.matchingengine.persistence.repository.OrderRepository;
import com.example.matchingengine.persistence.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive persistence service for orders and trades.
 *
 * <p>Maps domain objects to R2DBC entities and delegates to Spring Data
 * repositories. Final state is persisted in PostgreSQL.</p>
 */
@Service
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public PersistenceService(OrderRepository orderRepository, TradeRepository tradeRepository) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    /** Persist a new order. */
    public Mono<Void> persistOrder(Order order) {
        OrderEntity entity = new OrderEntity(
                order.getId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getType().name(),
                order.getPrice(),
                order.getOriginalQuantity(),
                order.getRemainingQuantity(),
                order.isFilled() ? "FILLED" : "ACTIVE"
        );
        return orderRepository.save(entity).then();
    }

    /** Persist a batch of trades. */
    public Mono<Void> persistTrades(List<Trade> trades) {
        List<TradeEntity> entities = trades.stream()
                .map(t -> new TradeEntity(
                        t.getTradeId(),
                        t.getBuyOrderId(),
                        t.getSellOrderId(),
                        t.getSymbol(),
                        t.getPrice(),
                        t.getQuantity()))
                .toList();

        return Flux.fromIterable(entities)
                .flatMap(tradeRepository::save)
                .then();
    }

    /** Update order status (e.g. CANCELLED, FILLED, PARTIALLY_FILLED). */
    public Mono<Void> updateOrderStatus(long orderId, String status) {
        return orderRepository.updateStatus(orderId, status).then();
    }

    /** Update order remaining quantity and status after a partial/full fill. */
    public Mono<Void> updateOrderFill(long orderId, long remainingQuantity) {
        String status = (remainingQuantity == 0) ? "FILLED" : "PARTIALLY_FILLED";
        return orderRepository.updateRemainingQuantity(orderId, remainingQuantity, status).then();
    }
}
