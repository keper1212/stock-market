package com.keper1212.stockmarket.domain.matching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.domain.order.entity.OutboxEvent;
import com.keper1212.stockmarket.domain.order.repository.OutboxEventRepository;
import com.keper1212.stockmarket.domain.realtime.service.RealtimePublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingEngineService {

    private static final String AGGREGATE_TYPE_TRADE = "TRADE";
    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String EVENT_TYPE_TRADE_EXECUTED = "TRADE_EXECUTED";
    private static final String EVENT_TYPE_ORDER_CANCELED = "ORDER_CANCELED";
    private static final String EVENT_TYPE_ORDER_REJECTED = "ORDER_REJECTED";
    private static final String TRADE_EVENT_TOPIC = "trade-events";

    private final OrderBookService orderBookService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RealtimePublisher realtimePublisher;

    @Transactional
    public String matchAcceptedOrder(JsonNode payload) {
        String stockCode = requiredText(payload, "stockCode");
        String matchResult = orderBookService.matchAcceptedOrder(payload);
        JsonNode result = parseResult(matchResult);

        realtimePublisher.requestMarketSnapshots(stockCode);

        publishTradeExecutedEvents(result);
        publishOrderRejectedEvent(result);
        return matchResult;
    }

    @Transactional
    public String cancelRequestedOrder(JsonNode payload) {
        String cancelResult = orderBookService.cancelRequestedOrder(payload);
        publishOrderCanceledEvent(cancelResult);
        return cancelResult;
    }


    private void publishTradeExecutedEvents(JsonNode result) {
        JsonNode trades = result.path("trades");
        if (!trades.isArray() || trades.isEmpty()) {
            return;
        }

        for (JsonNode trade : trades) {
            UUID eventId = UUID.randomUUID();
            JsonNode payload = objectMapper.valueToTree(new TradeExecutedOutboxPayload(
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
        JsonNode payload = objectMapper.valueToTree(new OrderCanceledOutboxPayload(
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
        JsonNode payload = objectMapper.valueToTree(new OrderRejectedOutboxPayload(
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

    private record TradeExecutedOutboxPayload(
            UUID tradeEventId,
            String stockCode,
            UUID buyOrderId,
            long buyerId,
            long buyOrderPrice,
            long buyOrderRemaining,
            String buyOrderStatus,
            UUID sellOrderId,
            long sellerId,
            long sellOrderPrice,
            long sellOrderRemaining,
            String sellOrderStatus,
            long tradePrice,
            long tradeQuantity,
            OffsetDateTime executedAt
    ) {
    }

    private record OrderCanceledOutboxPayload(
            UUID cancelEventId,
            UUID orderId,
            long userId,
            String stockCode,
            String orderType,
            long price,
            long canceledQuantity,
            String clientCancelId,
            OffsetDateTime canceledAt
    ) {
    }

    private record OrderRejectedOutboxPayload(
            UUID rejectEventId,
            UUID orderId,
            long userId,
            String stockCode,
            String orderType,
            long price,
            long rejectedQuantity,
            String reason,
            OffsetDateTime rejectedAt
    ) {
    }
}
