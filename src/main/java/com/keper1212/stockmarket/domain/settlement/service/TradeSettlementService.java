package com.keper1212.stockmarket.domain.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.keper1212.stockmarket.domain.order.entity.Trade;
import com.keper1212.stockmarket.domain.order.repository.OrderRepository;
import com.keper1212.stockmarket.domain.order.repository.TradeRepository;
import com.keper1212.stockmarket.domain.realtime.dto.TradeExecutedRealtimeMessage;
import com.keper1212.stockmarket.domain.realtime.service.RealtimePublisher;
import com.keper1212.stockmarket.domain.userservice.repository.AccountRepository;
import com.keper1212.stockmarket.domain.userservice.repository.UserStockRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class TradeSettlementService {

    private static final String ORDER_TYPE_BUY = "BUY";
    private static final String ORDER_TYPE_SELL = "SELL";

    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final UserStockRepository userStockRepository;
    private final RealtimePublisher realtimePublisher;

    @Transactional
    public boolean settle(JsonNode payload) {
        UUID tradeEventId = UUID.fromString(requiredText(payload, "tradeEventId"));
        if (tradeRepository.existsByTradeEventId(tradeEventId)) {
            return false;
        }

        String stockCode = requiredText(payload, "stockCode");
        UUID buyOrderId = UUID.fromString(requiredText(payload, "buyOrderId"));
        long buyerId = requiredLong(payload, "buyerId");
        long buyOrderPrice = requiredLong(payload, "buyOrderPrice");
        long buyOrderRemaining = requiredLong(payload, "buyOrderRemaining");
        String buyOrderStatus = requiredText(payload, "buyOrderStatus");
        UUID sellOrderId = UUID.fromString(requiredText(payload, "sellOrderId"));
        long sellerId = requiredLong(payload, "sellerId");
        long sellOrderRemaining = requiredLong(payload, "sellOrderRemaining");
        String sellOrderStatus = requiredText(payload, "sellOrderStatus");
        long tradePrice = requiredLong(payload, "tradePrice");
        long tradeQuantity = requiredLong(payload, "tradeQuantity");
        String executedAt = requiredText(payload, "executedAt");

        long buyerLockedAmount = multiplyExact(buyOrderPrice, tradeQuantity);
        long tradeAmount = multiplyExact(tradePrice, tradeQuantity);

        assertUpdated(accountRepository.settleBuyerCash(buyerId, buyerLockedAmount, tradeAmount), "매수자 예수금 정산에 실패했습니다.");
        assertUpdated(accountRepository.settleSellerCash(sellerId, tradeAmount), "매도자 예수금 정산에 실패했습니다.");
        userStockRepository.addBuyerStock(buyerId, stockCode, tradeQuantity, tradePrice);
        assertUpdated(userStockRepository.settleSellerStock(sellerId, stockCode, tradeQuantity), "매도자 보유주식 정산에 실패했습니다.");
        assertUpdated(orderRepository.updateExecutionState(buyOrderId, buyOrderRemaining, buyOrderStatus), "매수 주문 상태 갱신에 실패했습니다.");
        assertUpdated(orderRepository.updateExecutionState(sellOrderId, sellOrderRemaining, sellOrderStatus), "매도 주문 상태 갱신에 실패했습니다.");

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
        publishRealtimeAfterCommit(new TradeExecutedRealtimeMessage(
                stockCode,
                buyOrderId,
                sellOrderId,
                tradePrice,
                tradeQuantity,
                executedAt
        ));
        return true;
    }

    @Transactional
    public boolean cancel(JsonNode payload) {
        UUID orderId = UUID.fromString(requiredText(payload, "orderId"));
        long userId = requiredLong(payload, "userId");
        String stockCode = requiredText(payload, "stockCode");
        String orderType = requiredText(payload, "orderType");
        long price = requiredLong(payload, "price");
        long canceledQuantity = requiredLong(payload, "canceledQuantity");

        if (canceledQuantity <= 0) {
            return false;
        }

        int updatedRows = orderRepository.completeCancel(orderId, userId, canceledQuantity);
        if (updatedRows == 0) {
            return false;
        }

        if (ORDER_TYPE_BUY.equals(orderType)) {
            long unlockAmount = multiplyExact(price, canceledQuantity);
            assertUpdated(accountRepository.unlockCashByUserId(userId, unlockAmount), "매수 취소 예수금 잠금 해제에 실패했습니다.");
        } else if (ORDER_TYPE_SELL.equals(orderType)) {
            assertUpdated(userStockRepository.unlockQuantityByUserIdAndStockCode(userId, stockCode, canceledQuantity), "매도 취소 보유수량 잠금 해제에 실패했습니다.");
        } else {
            throw new IllegalArgumentException("Unsupported canceled order type: " + orderType);
        }

        publishMarketSnapshotsAfterCommit(stockCode);
        return true;
    }

    @Transactional
    public boolean reject(JsonNode payload) {
        UUID orderId = UUID.fromString(requiredText(payload, "orderId"));
        long userId = requiredLong(payload, "userId");
        String stockCode = requiredText(payload, "stockCode");
        String orderType = requiredText(payload, "orderType");
        long price = requiredLong(payload, "price");
        long rejectedQuantity = requiredLong(payload, "rejectedQuantity");

        if (rejectedQuantity <= 0) {
            return false;
        }

        int updatedRows = orderRepository.completeReject(orderId, userId, rejectedQuantity);
        if (updatedRows == 0) {
            return false;
        }

        if (ORDER_TYPE_BUY.equals(orderType)) {
            long unlockAmount = multiplyExact(price, rejectedQuantity);
            assertUpdated(accountRepository.unlockCashByUserId(userId, unlockAmount), "거부된 매수 주문 예수금 잠금 해제에 실패했습니다.");
        } else if (ORDER_TYPE_SELL.equals(orderType)) {
            assertUpdated(userStockRepository.unlockQuantityByUserIdAndStockCode(userId, stockCode, rejectedQuantity), "거부된 매도 주문 보유수량 잠금 해제에 실패했습니다.");
        } else {
            throw new IllegalArgumentException("Unsupported rejected order type: " + orderType);
        }

        publishMarketSnapshotsAfterCommit(stockCode);
        return true;
    }

    private void publishRealtimeAfterCommit(TradeExecutedRealtimeMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishRealtime(message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishRealtime(message);
            }
        });
    }

    private void publishRealtime(TradeExecutedRealtimeMessage message) {
        realtimePublisher.publishTradeExecuted(message);
        realtimePublisher.publishMarketSnapshots(message.stockCode());
    }

    private void publishMarketSnapshotsAfterCommit(String stockCode) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            realtimePublisher.publishMarketSnapshots(stockCode);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                realtimePublisher.publishMarketSnapshots(stockCode);
            }
        });
    }

    private long multiplyExact(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("정산 금액이 허용 범위를 초과했습니다.", e);
        }
    }

    private void assertUpdated(int updatedRows, String message) {
        if (updatedRows == 0) {
            throw new IllegalStateException(message);
        }
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
