package com.keper1212.stockmarket.domain.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.domain.realtime.dto.TradeExecutedRealtimeMessage;
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
@ConditionalOnProperty(prefix = "app.kafka.realtime-consumer", name = "enabled", havingValue = "true")
public class RealtimeMarketEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMarketEventConsumer.class);

    private static final String EVENT_TYPE_MARKET_TRADE_SETTLED = "MARKET_TRADE_SETTLED";
    private static final String EVENT_TYPE_MARKET_ORDERBOOK_CHANGED = "MARKET_ORDERBOOK_CHANGED";

    private final ObjectMapper objectMapper;
    private final RealtimePublisher realtimePublisher;

    @KafkaListener(topics = "market-events", groupId = "${app.kafka.realtime-consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            MarketEventMessage message = objectMapper.readValue(record.value(), MarketEventMessage.class);
            if (EVENT_TYPE_MARKET_TRADE_SETTLED.equals(message.eventType())) {
                handleMarketTradeSettled(message);
                acknowledgment.acknowledge();
                return;
            }

            if (EVENT_TYPE_MARKET_ORDERBOOK_CHANGED.equals(message.eventType())) {
                handleMarketOrderBookChanged(message);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Unsupported realtime market event type skipped. eventId={}, eventType={}, topic={}, partition={}, offset={}",
                    message.eventId(), message.eventType(), record.topic(), record.partition(), record.offset());
            acknowledgment.acknowledge();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Invalid realtime market event message skipped. topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private void handleMarketTradeSettled(MarketEventMessage message) {
        JsonNode payload = message.payload();
        String stockCode = requiredText(payload, "stockCode");
        realtimePublisher.publishTradeExecuted(new TradeExecutedRealtimeMessage(
                stockCode,
                UUID.fromString(requiredText(payload, "buyOrderId")),
                UUID.fromString(requiredText(payload, "sellOrderId")),
                requiredLong(payload, "tradePrice"),
                requiredLong(payload, "tradeQuantity"),
                requiredText(payload, "executedAt")
        ));
        realtimePublisher.requestMarketSnapshots(stockCode);
    }

    private void handleMarketOrderBookChanged(MarketEventMessage message) {
        realtimePublisher.requestMarketSnapshots(requiredText(message.payload(), "stockCode"));
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing realtime market event field: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing realtime market event field: " + fieldName);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid realtime market event field: " + fieldName, e);
        }
    }

    private record MarketEventMessage(
            UUID eventId,
            String eventType,
            JsonNode payload
    ) {
    }
}
