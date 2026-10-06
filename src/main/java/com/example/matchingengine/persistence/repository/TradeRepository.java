package com.example.matchingengine.persistence.repository;

import com.example.matchingengine.persistence.entity.TradeEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TradeRepository extends ReactiveCrudRepository<TradeEntity, Long> {

    Flux<TradeEntity> findBySymbol(String symbol);

    Flux<TradeEntity> findByBuyOrderIdOrSellOrderId(long buyOrderId, long sellOrderId);
}
