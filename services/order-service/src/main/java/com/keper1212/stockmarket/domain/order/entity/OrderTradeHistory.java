package com.keper1212.stockmarket.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** order-service가 소유하는 체결 이력 읽기 모델이다. */
@Entity
@Table(name = "order_trade_history")
public class OrderTradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_trade_history_id")
    private Long id;

    @Column(name = "trade_event_id", nullable = false, updatable = false)
    private UUID tradeEventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "order_type", nullable = false, length = 4)
    private String orderType;

    @Column(name = "trade_price", nullable = false)
    private long tradePrice;

    @Column(name = "trade_quantity", nullable = false)
    private long tradeQuantity;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    protected OrderTradeHistory() {
    }

    private OrderTradeHistory(UUID tradeEventId, UUID orderId, long userId, String stockCode, String orderType, long tradePrice, long tradeQuantity, OffsetDateTime executedAt) {
        this.tradeEventId = tradeEventId;
        this.orderId = orderId;
        this.userId = userId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.tradePrice = tradePrice;
        this.tradeQuantity = tradeQuantity;
        this.executedAt = executedAt;
    }

    public static OrderTradeHistory fromTrade(UUID tradeEventId, UUID orderId, long userId, String stockCode, String orderType, long tradePrice, long tradeQuantity, OffsetDateTime executedAt) {
        return new OrderTradeHistory(tradeEventId, orderId, userId, stockCode, orderType, tradePrice, tradeQuantity, executedAt);
    }
}
