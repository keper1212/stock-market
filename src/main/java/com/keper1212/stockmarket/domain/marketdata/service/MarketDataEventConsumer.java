package com.keper1212.stockmarket.domain.marketdata.service;

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
@ConditionalOnProperty(prefix = "app.kafka.marketdata-consumer", name = "enabled", havingValue = "true")
public class MarketDataEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketDataEventConsumer.class);

    private static final String EVENT_TYPE_MARKET_TRADE_SETTLED = "MARKET_TRADE_SETTLED";

    private final ObjectMapper objectMapper;
    private final StockMarketDataService stockMarketDataService;

    @KafkaListener(topics = "market-events", groupId = "${app.kafka.marketdata-consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            MarketEventMessage message = objectMapper.readValue(record.value(), MarketEventMessage.class);
            if (EVENT_TYPE_MARKET_TRADE_SETTLED.equals(message.eventType())) {
                handleMarketTradeSettled(message);
            }
            acknowledgment.acknowledge();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Invalid marketdata event message skipped. topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private void handleMarketTradeSettled(MarketEventMessage message) {
        JsonNode payload = message.payload();
        stockMarketDataService.recordTradeSnapshot(
                requiredText(payload, "stockCode"),
                requiredLong(payload, "tradePrice"),
                requiredLong(payload, "tradeQuantity")
        );
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing marketdata event field: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing marketdata event field: " + fieldName);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid marketdata event field: " + fieldName, e);
        }
    }

    private record MarketEventMessage(
            UUID eventId,
            String eventType,
            JsonNode payload
    ) {
    }
}
