package com.keper1212.stockmarket.asset.domain.asset.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.common.event.EventTypes;
import com.keper1212.stockmarket.common.event.KafkaTopics;
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
@ConditionalOnProperty(prefix = "app.kafka.asset-consumer", name = "enabled", havingValue = "true")
public class AssetEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssetEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final AssetService assetService;

    @KafkaListener(
            topics = {KafkaTopics.ASSET_COMMANDS, KafkaTopics.TRADE_EVENTS},
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            KafkaMessage message = objectMapper.readValue(record.value(), KafkaMessage.class);
            handle(message);
            acknowledgment.acknowledge();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Invalid asset event skipped. topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private void handle(KafkaMessage message) {
        if (EventTypes.ASSET_HOLD_REQUESTED.equals(message.eventType())) {
            assetService.reserveHold(message.eventId(), message.payload());
            return;
        }
        if (EventTypes.TRADE_EXECUTED.equals(message.eventType())) {
            assetService.settleTrade(message.eventId(), message.payload());
            return;
        }
        if (EventTypes.ORDER_CANCELED.equals(message.eventType())) {
            assetService.releaseCanceledOrder(message.eventId(), message.payload());
            return;
        }
        if (EventTypes.ORDER_REJECTED.equals(message.eventType())) {
            assetService.releaseRejectedOrder(message.eventId(), message.payload());
        }
    }

    private record KafkaMessage(UUID eventId, String eventType, JsonNode payload) {
    }
}
