package com.example.matchingengine.kafka;

import com.example.matchingengine.domain.Order;
import com.example.matchingengine.domain.OrderType;
import com.example.matchingengine.domain.Side;
import com.example.matchingengine.dto.OrderRequest;
import com.example.matchingengine.service.IdGeneratorService;
import com.example.matchingengine.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

/**
 * Reactive Kafka consumer that reads inbound order messages from the
 * {@code orders.inbound} topic and feeds them into the matching engine.
 */
@Component
@ConditionalOnProperty(name = "matching-engine.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class OrderKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderKafkaConsumer.class);

    private final KafkaReceiver<String, String> receiver;
    private final MatchingEngineService engineService;
    private final IdGeneratorService idGeneratorService;
    private final ObjectMapper objectMapper;

    public OrderKafkaConsumer(ReceiverOptions<String, String> receiverOptions,
                              MatchingEngineService engineService,
                              IdGeneratorService idGeneratorService,
                              ObjectMapper objectMapper) {
        this.receiver = KafkaReceiver.create(receiverOptions);
        this.engineService = engineService;
        this.idGeneratorService = idGeneratorService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startConsuming() {
        receiver.receive()
                .flatMap(record -> {
                    try {
                        OrderRequest req = objectMapper.readValue(record.value(), OrderRequest.class);
                        Order order = new Order(
                                idGeneratorService.nextId(),
                                req.symbol(),
                                Side.valueOf(req.side().toUpperCase()),
                                OrderType.valueOf(req.type().toUpperCase()),
                                req.price(),
                                req.quantity(),
                                System.nanoTime());

                        return engineService.submitOrder(order)
                                .doOnNext(trades ->
                                        log.info("Kafka order processed: id={} symbol={} trades={}",
                                                order.getId(), order.getSymbol(), trades.size()))
                                .doFinally(sig -> record.receiverOffset().acknowledge());
                    } catch (Exception e) {
                        log.error("Failed to process Kafka order: {}", e.getMessage(), e);
                        record.receiverOffset().acknowledge();
                        return reactor.core.publisher.Mono.empty();
                    }
                })
                .doOnError(e -> log.error("Kafka consumer error", e))
                .retry()
                .subscribe();
    }
}
