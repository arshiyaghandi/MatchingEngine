package com.example.matchingengine.kafka;

import com.example.matchingengine.domain.Trade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.util.List;

/**
 * Publishes trade events to the {@code trades.outbound} Kafka topic.
 *
 * <p>Trades are serialised as JSON strings. The partition key is the symbol
 * so that all trades for a given instrument land on the same partition
 * (preserving order).</p>
 */
@Component
@ConditionalOnProperty(name = "matching-engine.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class TradeKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeKafkaProducer.class);

    private final KafkaSender<String, String> sender;
    private final ObjectMapper objectMapper;
    private final String outboundTopic;

    public TradeKafkaProducer(
            SenderOptions<String, String> senderOptions,
            ObjectMapper objectMapper,
            @Value("${matching-engine.kafka.outbound-topic}") String outboundTopic) {
        this.sender = KafkaSender.create(senderOptions);
        this.objectMapper = objectMapper;
        this.outboundTopic = outboundTopic;
    }

    /**
     * Publishes a batch of trades to Kafka.
     *
     * @return completes when all records have been acknowledged
     */
    public Mono<Void> publishTrades(List<Trade> trades) {
        if (trades.isEmpty()) {
            return Mono.empty();
        }

        Flux<SenderRecord<String, String, Long>> records = Flux.fromIterable(trades)
                .map(trade -> {
                    String json = serialise(trade);
                    ProducerRecord<String, String> record =
                            new ProducerRecord<>(outboundTopic, trade.getSymbol(), json);
                    return SenderRecord.create(record, trade.getTradeId());
                });

        return sender.send(records)
                .doOnNext(result -> log.debug("Trade {} published to partition {} offset {}",
                        result.correlationMetadata(),
                        result.recordMetadata().partition(),
                        result.recordMetadata().offset()))
                .then();
    }

    private String serialise(Trade trade) {
        try {
            return objectMapper.writeValueAsString(trade);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise trade: " + trade.getTradeId(), e);
        }
    }
}
