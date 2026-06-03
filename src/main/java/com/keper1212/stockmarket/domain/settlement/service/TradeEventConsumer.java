package com.keper1212.stockmarket.domain.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka.consumer", name = "enabled", havingValue = "true")
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);

    private static final String EVENT_TYPE_TRADE_EXECUTED = "TRADE_EXECUTED";

    private final ObjectMapper objectMapper;
    private final TradeSettlementService tradeSettlementService;

    @KafkaListener(topics = "trade-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            TradeEventMessage message = objectMapper.readValue(record.value(), TradeEventMessage.class);
            if (!EVENT_TYPE_TRADE_EXECUTED.equals(message.eventType())) {
                log.warn("Unsupported trade event type skipped. eventId={}, eventType={}, topic={}, partition={}, offset={}",
                        message.eventId(), message.eventType(), record.topic(), record.partition(), record.offset());
                acknowledgment.acknowledge();
                return;
            }

            boolean settled = tradeSettlementService.settle(message.payload());
            JsonNode payload = message.payload();
            log.info("TRADE_EXECUTED consumed. eventId={}, tradeEventId={}, stockCode={}, buyOrderId={}, sellOrderId={}, tradePrice={}, tradeQuantity={}, dbSettled={}, partition={}, offset={}",
                    message.eventId(),
                    payload.path("tradeEventId").asText(),
                    payload.path("stockCode").asText(),
                    payload.path("buyOrderId").asText(),
                    payload.path("sellOrderId").asText(),
                    payload.path("tradePrice").asLong(),
                    payload.path("tradeQuantity").asLong(),
                    settled,
                    record.partition(),
                    record.offset());
            acknowledgment.acknowledge();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Invalid trade event message skipped. topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private record TradeEventMessage(
            UUID eventId,
            String eventType,
            JsonNode payload
    ) {
    }
}
