package com.keper1212.stockmarket.domain.matching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.domain.order.entity.OutboxEvent;
import com.keper1212.stockmarket.domain.order.repository.OutboxEventRepository;
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
    private static final String EVENT_TYPE_TRADE_EXECUTED = "TRADE_EXECUTED";
    private static final String TRADE_EVENT_TOPIC = "trade-events";

    private final OrderBookService orderBookService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String matchAcceptedOrder(JsonNode payload) {
        String matchResult = orderBookService.matchAcceptedOrder(payload);
        publishTradeExecutedEvents(matchResult);
        return matchResult;
    }

    public boolean markCancelRequested(JsonNode payload) {
        return orderBookService.markCancelRequested(payload);
    }

    private void publishTradeExecutedEvents(String matchResult) {
        JsonNode result = parseResult(matchResult);
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
}
