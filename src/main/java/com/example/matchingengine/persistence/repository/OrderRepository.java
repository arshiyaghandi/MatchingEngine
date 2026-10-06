package com.example.matchingengine.persistence.repository;

import com.example.matchingengine.persistence.entity.OrderEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, Long> {

    Flux<OrderEntity> findBySymbol(String symbol);

    Flux<OrderEntity> findBySymbolAndStatus(String symbol, String status);

    @Modifying
    @Query("UPDATE orders SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    Mono<Integer> updateStatus(Long id, String status);

    @Modifying
    @Query("UPDATE orders SET remaining_quantity = :remainingQuantity, status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    Mono<Integer> updateRemainingQuantity(Long id, long remainingQuantity, String status);
}
