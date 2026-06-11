package com.keper1212.stockmarket.domain.matching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.common.event.EventTypes;
import com.keper1212.stockmarket.common.event.KafkaTopics;
import com.keper1212.stockmarket.common.event.MarketOrderBookChangedEvent;
import com.keper1212.stockmarket.common.event.OrderCanceledEvent;
import com.keper1212.stockmarket.common.event.OrderRejectedEvent;
import com.keper1212.stockmarket.common.event.OutboxKafkaMessage;
import com.keper1212.stockmarket.common.event.TradeExecutedEvent;
import com.keper1212.stockmarket.domain.order.entity.OutboxEvent;
import com.keper1212.stockmarket.domain.order.repository.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingEngineService {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineService.class);

    private static final String AGGREGATE_TYPE_TRADE = "TRADE";
    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String EVENT_TYPE_TRADE_EXECUTED = EventTypes.TRADE_EXECUTED;
    private static final String EVENT_TYPE_ORDER_CANCELED = EventTypes.ORDER_CANCELED;
    private static final String EVENT_TYPE_ORDER_REJECTED = EventTypes.ORDER_REJECTED;
    private static final String EVENT_TYPE_MARKET_ORDERBOOK_CHANGED = EventTypes.MARKET_ORDERBOOK_CHANGED;
    private static final String TRADE_EVENT_TOPIC = KafkaTopics.TRADE_EVENTS;
    private static final String MARKET_EVENT_TOPIC = KafkaTopics.MARKET_EVENTS;

    private final OrderBookService orderBookService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public String matchAcceptedOrder(JsonNode payload) {
        String stockCode = requiredText(payload, "stockCode");
        String matchResult = orderBookService.matchAcceptedOrder(payload);
        JsonNode result = parseResult(matchResult);

        sendMarketOrderBookChangedEvent(stockCode);
        publishTradeExecutedEvents(result);
        publishOrderRejectedEvent(result);
        return matchResult;
    }

    @Transactional
    public String cancelRequestedOrder(JsonNode payload) {
        String cancelResult = orderBookService.cancelRequestedOrder(payload);
        publishOrderCanceledEvent(cancelResult);
        JsonNode result = parseResult(cancelResult);
        if ("CANCELED".equals(requiredText(result, "result"))) {
            sendMarketOrderBookChangedEvent(requiredText(result, "stockCode"));
        }
        return cancelResult;
    }


    private void sendMarketOrderBookChangedEvent(String stockCode) {
        try {
            UUID eventId = UUID.randomUUID();
            JsonNode payload = objectMapper.valueToTree(new MarketOrderBookChangedEvent(
                    eventId,
                    stockCode,
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
            String message = objectMapper.writeValueAsString(new OutboxKafkaMessage(
                    eventId,
                    EVENT_TYPE_MARKET_ORDERBOOK_CHANGED,
                    payload
            ));
            kafkaTemplate.send(MARKET_EVENT_TOPIC, stockCode, message);
        } catch (Exception e) {
            log.warn("Market orderbook changed event publish failed. stockCode={}, error={}", stockCode, e.getMessage());
        }
    }

    private void publishTradeExecutedEvents(JsonNode result) {
        JsonNode trades = result.path("trades");
        if (!trades.isArray() || trades.isEmpty()) {
            return;
        }

        for (JsonNode trade : trades) {
            UUID eventId = UUID.randomUUID();
            JsonNode payload = objectMapper.valueToTree(new TradeExecutedEvent(
                    eventId,
                    requiredText(trade, "stockCode"),
                    UUID.fromString(requiredText(trade, "buyOrderId")),
                    requiredLong(trade, "buyerId"),
                    requiredLong(trade, "buyOrderPrice"),
                    requiredLong(trade, "buyOrderRemaining"),
                    requiredText(trade, "buyOrderStatus"),
                    UUID.fromString(requiredText(trade, "sellOrderId")),
                    requiredLong(trade, "sellerId"),
                    requiredLong(trade, "sellOrderPrice"),
                    requiredLong(trade, "sellOrderRemaining"),
                    requiredText(trade, "sellOrderStatus"),
                    requiredLong(trade, "tradePrice"),
                    requiredLong(trade, "tradeQuantity"),
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));

            outboxEventRepository.save(OutboxEvent.pending(
                    eventId,
                    AGGREGATE_TYPE_TRADE,
                    eventId.toString(),
                    EVENT_TYPE_TRADE_EXECUTED,
                    TRADE_EVENT_TOPIC,
                    requiredText(trade, "stockCode"),
                    payload
            ));
        }
    }

    private void publishOrderCanceledEvent(String cancelResult) {
        JsonNode result = parseResult(cancelResult);
        if (!"CANCELED".equals(requiredText(result, "result"))) {
            return;
        }

        long canceledQuantity = requiredLong(result, "canceledQuantity");
        if (canceledQuantity <= 0) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.valueToTree(new OrderCanceledEvent(
                eventId,
                UUID.fromString(requiredText(result, "orderId")),
                requiredLong(result, "userId"),
                requiredText(result, "stockCode"),
                requiredText(result, "orderType"),
                requiredLong(result, "price"),
                canceledQuantity,
                requiredText(result, "clientCancelId"),
                OffsetDateTime.parse(requiredText(result, "canceledAt"))
        ));

        outboxEventRepository.save(OutboxEvent.pending(
                eventId,
                AGGREGATE_TYPE_ORDER,
                requiredText(result, "orderId"),
                EVENT_TYPE_ORDER_CANCELED,
                TRADE_EVENT_TOPIC,
                requiredText(result, "stockCode"),
                payload
        ));
    }

    private void publishOrderRejectedEvent(JsonNode result) {
        JsonNode rejection = result.path("selfTradePrevention");
        if (rejection.isMissingNode() || rejection.isNull()) {
            return;
        }

        long rejectedQuantity = requiredLong(rejection, "rejectedQuantity");
        if (rejectedQuantity <= 0) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.valueToTree(new OrderRejectedEvent(
                eventId,
                UUID.fromString(requiredText(rejection, "orderId")),
                requiredLong(rejection, "userId"),
                requiredText(rejection, "stockCode"),
                requiredText(rejection, "orderType"),
                requiredLong(rejection, "price"),
                rejectedQuantity,
                requiredText(rejection, "reason"),
                OffsetDateTime.now(ZoneOffset.UTC)
        ));

        outboxEventRepository.save(OutboxEvent.pending(
                eventId,
                AGGREGATE_TYPE_ORDER,
                requiredText(rejection, "orderId"),
                EVENT_TYPE_ORDER_REJECTED,
                TRADE_EVENT_TOPIC,
                requiredText(rejection, "stockCode"),
                payload
        ));
    }

    private JsonNode parseResult(String matchResult) {
        try {
            return objectMapper.readTree(matchResult);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Redis match result: " + e.getMessage(), e);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing trade event field: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing trade event field: " + fieldName);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid trade event field: " + fieldName, e);
        }
    }


}
