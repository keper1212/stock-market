package com.keper1212.stockmarket.domain.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.common.event.EventTypes;
import com.keper1212.stockmarket.common.event.KafkaTopics;
import com.keper1212.stockmarket.common.event.MarketTradeSettledEvent;
import com.keper1212.stockmarket.domain.settlement.entity.SettlementOutboxEvent;
import com.keper1212.stockmarket.domain.settlement.entity.Trade;
import com.keper1212.stockmarket.domain.settlement.repository.SettlementOutboxEventRepository;
import com.keper1212.stockmarket.domain.settlement.repository.TradeRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradeSettlementService {

    private static final String AGGREGATE_TYPE_MARKET = "MARKET";
    private static final String EVENT_TYPE_MARKET_TRADE_SETTLED = EventTypes.MARKET_TRADE_SETTLED;
    private static final String MARKET_EVENT_TOPIC = KafkaTopics.MARKET_EVENTS;

    private final TradeRepository tradeRepository;
    private final SettlementOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean settle(JsonNode payload) {
        UUID tradeEventId = UUID.fromString(requiredText(payload, "tradeEventId"));
        if (tradeRepository.existsByTradeEventId(tradeEventId)) {
            return false;
        }

        String stockCode = requiredText(payload, "stockCode");
        UUID buyOrderId = UUID.fromString(requiredText(payload, "buyOrderId"));
        long buyerId = requiredLong(payload, "buyerId");
        UUID sellOrderId = UUID.fromString(requiredText(payload, "sellOrderId"));
        long sellerId = requiredLong(payload, "sellerId");
        long tradePrice = requiredLong(payload, "tradePrice");
        long tradeQuantity = requiredLong(payload, "tradeQuantity");
        String executedAt = requiredText(payload, "executedAt");

        tradeRepository.save(Trade.executed(
                tradeEventId,
                stockCode,
                buyerId,
                sellerId,
                buyOrderId,
                sellOrderId,
                tradePrice,
                tradeQuantity
        ));
        publishMarketTradeSettledEvent(
                stockCode,
                buyOrderId,
                sellOrderId,
                tradePrice,
                tradeQuantity,
                executedAt
        );
        return true;
    }

    private void publishMarketTradeSettledEvent(
            String stockCode,
            UUID buyOrderId,
            UUID sellOrderId,
            long tradePrice,
            long tradeQuantity,
            String executedAt
    ) {
        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.valueToTree(new MarketTradeSettledEvent(
                eventId,
                stockCode,
                buyOrderId,
                sellOrderId,
                tradePrice,
                tradeQuantity,
                executedAt
        ));

        outboxEventRepository.save(SettlementOutboxEvent.pending(
                eventId,
                AGGREGATE_TYPE_MARKET,
                stockCode,
                EVENT_TYPE_MARKET_TRADE_SETTLED,
                MARKET_EVENT_TOPIC,
                stockCode,
                payload
        ));
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing trade settlement field: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing trade settlement field: " + fieldName);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid trade settlement field: " + fieldName, e);
        }
    }
}
