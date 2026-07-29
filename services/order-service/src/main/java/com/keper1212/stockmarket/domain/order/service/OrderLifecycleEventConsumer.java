package com.keper1212.stockmarket.domain.order.service;

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
@ConditionalOnProperty(prefix = "app.kafka.order-lifecycle-consumer", name = "enabled", havingValue = "true")
public class OrderLifecycleEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderLifecycleService orderLifecycleService;

    @KafkaListener(
            topics = {KafkaTopics.ASSET_EVENTS, KafkaTopics.TRADE_EVENTS},
            groupId = "${app.kafka.order-lifecycle-consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            KafkaMessage message = objectMapper.readValue(record.value(), KafkaMessage.class);
            handle(message);
            acknowledgment.acknowledge();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Invalid order lifecycle event skipped. topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private void handle(KafkaMessage message) {
        if (EventTypes.ASSET_HOLD_SUCCEEDED.equals(message.eventType())) {
            orderLifecycleService.acceptAfterAssetHold(message.payload());
            return;
        }
        if (EventTypes.ASSET_HOLD_FAILED.equals(message.eventType())) {
            orderLifecycleService.rejectAfterAssetHold(message.payload());
            return;
        }
        if (EventTypes.TRADE_EXECUTED.equals(message.eventType())) {
            orderLifecycleService.applyTradeExecution(message.payload());
            return;
        }
        if (EventTypes.ORDER_CANCELED.equals(message.eventType())) {
            orderLifecycleService.applyCancellation(message.payload());
            return;
        }
        if (EventTypes.ORDER_REJECTED.equals(message.eventType())) {
            orderLifecycleService.applyRejection(message.payload());
        }
    }

    private record KafkaMessage(UUID eventId, String eventType, JsonNode payload) {
    }
}
