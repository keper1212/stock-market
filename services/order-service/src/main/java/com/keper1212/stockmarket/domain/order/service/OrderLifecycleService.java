package com.keper1212.stockmarket.domain.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.common.event.EventTypes;
import com.keper1212.stockmarket.common.event.KafkaTopics;
import com.keper1212.stockmarket.common.event.OrderAcceptedEvent;
import com.keper1212.stockmarket.domain.order.entity.OrderOutboxEvent;
import com.keper1212.stockmarket.domain.order.entity.OrderTradeHistory;
import com.keper1212.stockmarket.domain.order.repository.OrderRepository;
import com.keper1212.stockmarket.domain.order.repository.OrderOutboxEventRepository;
import com.keper1212.stockmarket.domain.order.repository.OrderTradeHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final OrderOutboxEventRepository outboxEventRepository;
    private final OrderTradeHistoryRepository orderTradeHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean acceptAfterAssetHold(JsonNode payload) {
        UUID orderId = UUID.fromString(requiredText(payload, "orderId"));
        if (orderRepository.acceptAfterAssetHold(orderId) == 0) {
            return false;
        }

        OrderAcceptedEvent event = new OrderAcceptedEvent(
                orderId,
                requiredLong(payload, "userId"),
                requiredText(payload, "stockCode"),
                requiredText(payload, "orderType"),
                requiredLong(payload, "price"),
                requiredLong(payload, "quantity"),
                requiredLong(payload, "quantity"),
                requiredText(payload, "clientOrderId"),
                requiredOffsetDateTime(payload, "acceptedAt")
        );
        outboxEventRepository.save(OrderOutboxEvent.pending(
                UUID.randomUUID(),
                "ORDER",
                orderId.toString(),
                EventTypes.ORDER_ACCEPTED,
                KafkaTopics.ORDER_EVENTS,
                event.stockCode(),
                objectMapper.valueToTree(event)
        ));
        return true;
    }

    @Transactional
    public boolean rejectAfterAssetHold(JsonNode payload) {
        return orderRepository.rejectAfterAssetHold(UUID.fromString(requiredText(payload, "orderId"))) == 1;
    }

    @Transactional
    public void applyTradeExecution(JsonNode payload) {
        UUID tradeEventId = UUID.fromString(requiredText(payload, "tradeEventId"));
        UUID buyOrderId = UUID.fromString(requiredText(payload, "buyOrderId"));
        UUID sellOrderId = UUID.fromString(requiredText(payload, "sellOrderId"));
        String stockCode = requiredText(payload, "stockCode");
        long tradePrice = requiredLong(payload, "tradePrice");
        long tradeQuantity = requiredLong(payload, "tradeQuantity");
        OffsetDateTime executedAt = requiredOffsetDateTime(payload, "executedAt");

        orderRepository.updateExecutionState(
                buyOrderId,
                requiredLong(payload, "buyOrderRemaining"),
                requiredText(payload, "buyOrderStatus")
        );
        orderRepository.updateExecutionState(
                sellOrderId,
                requiredLong(payload, "sellOrderRemaining"),
                requiredText(payload, "sellOrderStatus")
        );

        saveTradeHistoryIfAbsent(
                tradeEventId,
                buyOrderId,
                requiredLong(payload, "buyerId"),
                stockCode,
                "BUY",
                tradePrice,
                tradeQuantity,
                executedAt
        );
        saveTradeHistoryIfAbsent(
                tradeEventId,
                sellOrderId,
                requiredLong(payload, "sellerId"),
                stockCode,
                "SELL",
                tradePrice,
                tradeQuantity,
                executedAt
        );
    }

    private void saveTradeHistoryIfAbsent(
            UUID tradeEventId,
            UUID orderId,
            long userId,
            String stockCode,
            String orderType,
            long tradePrice,
            long tradeQuantity,
            OffsetDateTime executedAt
    ) {
        if (orderTradeHistoryRepository.existsByTradeEventIdAndOrderId(tradeEventId, orderId)) {
            return;
        }
        orderTradeHistoryRepository.save(OrderTradeHistory.fromTrade(
                tradeEventId,
                orderId,
                userId,
                stockCode,
                orderType,
                tradePrice,
                tradeQuantity,
                executedAt
        ));
    }

    @Transactional
    public void applyCancellation(JsonNode payload) {
        orderRepository.completeCancel(
                UUID.fromString(requiredText(payload, "orderId")),
                requiredLong(payload, "userId"),
                requiredLong(payload, "canceledQuantity")
        );
    }

    @Transactional
    public void applyRejection(JsonNode payload) {
        orderRepository.completeReject(
                UUID.fromString(requiredText(payload, "orderId")),
                requiredLong(payload, "userId"),
                requiredLong(payload, "rejectedQuantity")
        );
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing order lifecycle field: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode payload, String fieldName) {
        try {
            return Long.parseLong(requiredText(payload, fieldName));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid order lifecycle field: " + fieldName, e);
        }
    }

    private OffsetDateTime requiredOffsetDateTime(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing order lifecycle field: " + fieldName);
        }

        try {
            if (value.isNumber()) {
                BigDecimal epochSeconds = value.decimalValue();
                long seconds = epochSeconds.longValue();
                long nanos = epochSeconds
                        .subtract(BigDecimal.valueOf(seconds))
                        .movePointRight(9)
                        .setScale(0, RoundingMode.DOWN)
                        .longValue();
                return OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(value.asText());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid order lifecycle field: " + fieldName, e);
        }
    }
}
