package com.keper1212.stockmarket.domain.matching.service;

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
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private static final String EVENT_TYPE_ORDER_ACCEPTED = "ORDER_ACCEPTED";
    private static final String EVENT_TYPE_ORDER_CANCEL_REQUESTED = "ORDER_CANCEL_REQUESTED";

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            OrderEventMessage message = objectMapper.readValue(record.value(), OrderEventMessage.class);
            handle(message, record);
        } catch (JsonProcessingException e) {
            log.warn("Invalid order event message skipped. topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage());
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private void handle(OrderEventMessage message, ConsumerRecord<String, String> record) {
        if (EVENT_TYPE_ORDER_ACCEPTED.equals(message.eventType())) {
            handleOrderAccepted(message, record);
            return;
        }

        if (EVENT_TYPE_ORDER_CANCEL_REQUESTED.equals(message.eventType())) {
            handleOrderCancelRequested(message, record);
            return;
        }

        log.warn("Unsupported order event type skipped. eventId={}, eventType={}, topic={}, partition={}, offset={}",
                message.eventId(), message.eventType(), record.topic(), record.partition(), record.offset());
    }

    private void handleOrderAccepted(OrderEventMessage message, ConsumerRecord<String, String> record) {
        JsonNode payload = message.payload();
        log.info("ORDER_ACCEPTED consumed. eventId={}, orderId={}, stockCode={}, orderType={}, price={}, quantity={}, partition={}, offset={}",
                message.eventId(),
                payload.path("orderId").asText(),
                payload.path("stockCode").asText(),
                payload.path("orderType").asText(),
                payload.path("price").asLong(),
                payload.path("quantity").asLong(),
                record.partition(),
                record.offset());
    }

    private void handleOrderCancelRequested(OrderEventMessage message, ConsumerRecord<String, String> record) {
        JsonNode payload = message.payload();
        log.info("ORDER_CANCEL_REQUESTED consumed. eventId={}, orderId={}, stockCode={}, clientCancelId={}, partition={}, offset={}",
                message.eventId(),
                payload.path("orderId").asText(),
                payload.path("stockCode").asText(),
                payload.path("clientCancelId").asText(),
                record.partition(),
                record.offset());
    }

    private record OrderEventMessage(
            UUID eventId,
            String eventType,
            JsonNode payload
    ) {
    }
}
