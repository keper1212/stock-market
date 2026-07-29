package com.keper1212.stockmarket.asset.domain.asset.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.asset.domain.asset.entity.AssetOutboxEvent;
import com.keper1212.stockmarket.asset.domain.asset.entity.ProcessedAssetEvent;
import com.keper1212.stockmarket.asset.domain.asset.repository.AccountRepository;
import com.keper1212.stockmarket.asset.domain.asset.repository.AssetOutboxEventRepository;
import com.keper1212.stockmarket.asset.domain.asset.repository.ProcessedAssetEventRepository;
import com.keper1212.stockmarket.asset.domain.asset.repository.UserStockRepository;
import com.keper1212.stockmarket.common.event.AssetHoldFailedEvent;
import com.keper1212.stockmarket.common.event.AssetHoldRequestedEvent;
import com.keper1212.stockmarket.common.event.AssetHoldSucceededEvent;
import com.keper1212.stockmarket.common.event.EventTypes;
import com.keper1212.stockmarket.common.event.KafkaTopics;
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
public class AssetService {

    private static final String ORDER_TYPE_BUY = "BUY";
    private static final String ORDER_TYPE_SELL = "SELL";
    private static final String AGGREGATE_TYPE_ASSET = "ASSET";

    private final AccountRepository accountRepository;
    private final UserStockRepository userStockRepository;
    private final ProcessedAssetEventRepository processedAssetEventRepository;
    private final AssetOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean reserveHold(UUID messageEventId, JsonNode payload) {
        if (alreadyProcessed(messageEventId)) {
            return false;
        }

        AssetHoldRequestedEvent request = readHoldRequest(payload);
        String failureReason = reserve(request);
        processedAssetEventRepository.save(new ProcessedAssetEvent(messageEventId));

        if (failureReason == null) {
            publishHoldSucceeded(request);
        } else {
            publishHoldFailed(request, failureReason);
        }
        return true;
    }

    @Transactional
    public boolean settleTrade(UUID messageEventId, JsonNode payload) {
        if (alreadyProcessed(messageEventId)) {
            return false;
        }

        String stockCode = requiredText(payload, "stockCode");
        long buyerId = requiredLong(payload, "buyerId");
        long buyOrderPrice = requiredLong(payload, "buyOrderPrice");
        long sellerId = requiredLong(payload, "sellerId");
        long tradePrice = requiredLong(payload, "tradePrice");
        long tradeQuantity = requiredLong(payload, "tradeQuantity");

        long buyerLockedAmount = multiplyExact(buyOrderPrice, tradeQuantity);
        long tradeAmount = multiplyExact(tradePrice, tradeQuantity);
        assertUpdated(accountRepository.settleBuyerCash(buyerId, buyerLockedAmount, tradeAmount), "매수자 예수금 정산에 실패했습니다.");
        assertUpdated(accountRepository.settleSellerCash(sellerId, tradeAmount), "매도자 예수금 정산에 실패했습니다.");
        userStockRepository.addBuyerStock(buyerId, stockCode, tradeQuantity, tradePrice);
        assertUpdated(userStockRepository.settleSellerStock(sellerId, stockCode, tradeQuantity), "매도자 보유주식 정산에 실패했습니다.");

        processedAssetEventRepository.save(new ProcessedAssetEvent(messageEventId));
        return true;
    }

    @Transactional
    public boolean releaseCanceledOrder(UUID messageEventId, JsonNode payload) {
        return releaseOrderReservation(messageEventId, payload, "canceledQuantity");
    }

    @Transactional
    public boolean releaseRejectedOrder(UUID messageEventId, JsonNode payload) {
        return releaseOrderReservation(messageEventId, payload, "rejectedQuantity");
    }

    private boolean releaseOrderReservation(UUID messageEventId, JsonNode payload, String quantityField) {
        if (alreadyProcessed(messageEventId)) {
            return false;
        }

        long userId = requiredLong(payload, "userId");
        String stockCode = requiredText(payload, "stockCode");
        String orderType = requiredText(payload, "orderType");
        long price = requiredLong(payload, "price");
        long quantity = requiredLong(payload, quantityField);

        if (quantity <= 0) {
            processedAssetEventRepository.save(new ProcessedAssetEvent(messageEventId));
            return false;
        }

        if (ORDER_TYPE_BUY.equals(orderType)) {
            assertUpdated(accountRepository.unlockCash(userId, multiplyExact(price, quantity)), "매수 주문 잠금 해제에 실패했습니다.");
        } else if (ORDER_TYPE_SELL.equals(orderType)) {
            assertUpdated(userStockRepository.unlockQuantity(userId, stockCode, quantity), "매도 주문 잠금 해제에 실패했습니다.");
        } else {
            throw new IllegalArgumentException("Unsupported order type: " + orderType);
        }

        processedAssetEventRepository.save(new ProcessedAssetEvent(messageEventId));
        return true;
    }

    private String reserve(AssetHoldRequestedEvent request) {
        if (ORDER_TYPE_BUY.equals(request.orderType())) {
            int updatedRows = accountRepository.lockCashIfAvailable(request.userId(), multiplyExact(request.price(), request.quantity()));
            return updatedRows == 1 ? null : "INSUFFICIENT_CASH";
        }
        if (ORDER_TYPE_SELL.equals(request.orderType())) {
            int updatedRows = userStockRepository.lockQuantityIfAvailable(request.userId(), request.stockCode(), request.quantity());
            return updatedRows == 1 ? null : "INSUFFICIENT_STOCK";
        }
        throw new IllegalArgumentException("Unsupported order type: " + request.orderType());
    }

    private void publishHoldSucceeded(AssetHoldRequestedEvent request) {
        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.valueToTree(new AssetHoldSucceededEvent(
                request.requestId(),
                request.orderId(),
                request.userId(),
                request.stockCode(),
                request.orderType(),
                request.price(),
                request.quantity(),
                request.clientOrderId(),
                request.requestedAt()
        ));
        outboxEventRepository.save(AssetOutboxEvent.pending(
                eventId,
                AGGREGATE_TYPE_ASSET,
                request.orderId().toString(),
                EventTypes.ASSET_HOLD_SUCCEEDED,
                KafkaTopics.ASSET_EVENTS,
                request.orderId().toString(),
                payload
        ));
    }

    private void publishHoldFailed(AssetHoldRequestedEvent request, String reason) {
        UUID eventId = UUID.randomUUID();
        JsonNode payload = objectMapper.valueToTree(new AssetHoldFailedEvent(
                request.requestId(),
                request.orderId(),
                request.userId(),
                request.stockCode(),
                request.orderType(),
                request.price(),
                request.quantity(),
                request.clientOrderId(),
                reason,
                OffsetDateTime.now(ZoneOffset.UTC)
        ));
        outboxEventRepository.save(AssetOutboxEvent.pending(
                eventId,
                AGGREGATE_TYPE_ASSET,
                request.orderId().toString(),
                EventTypes.ASSET_HOLD_FAILED,
                KafkaTopics.ASSET_EVENTS,
                request.orderId().toString(),
                payload
        ));
    }

    private AssetHoldRequestedEvent readHoldRequest(JsonNode payload) {
        return new AssetHoldRequestedEvent(
                UUID.fromString(requiredText(payload, "requestId")),
                UUID.fromString(requiredText(payload, "orderId")),
                requiredLong(payload, "userId"),
                requiredText(payload, "stockCode"),
                requiredText(payload, "orderType"),
                requiredLong(payload, "price"),
                requiredLong(payload, "quantity"),
                requiredText(payload, "clientOrderId"),
                requiredOffsetDateTime(payload, "requestedAt")
        );
    }

    private boolean alreadyProcessed(UUID eventId) {
        return processedAssetEventRepository.existsById(eventId);
    }

    private long multiplyExact(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("자산 처리 금액이 허용 범위를 초과했습니다.", e);
        }
    }

    private void assertUpdated(int updatedRows, String message) {
        if (updatedRows != 1) {
            throw new IllegalStateException(message);
        }
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing asset event field: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode payload, String fieldName) {
        try {
            return Long.parseLong(requiredText(payload, fieldName));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid asset event field: " + fieldName, e);
        }
    }

    private OffsetDateTime requiredOffsetDateTime(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing asset event field: " + fieldName);
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
            throw new IllegalArgumentException("Invalid asset event field: " + fieldName, e);
        }
    }
}
