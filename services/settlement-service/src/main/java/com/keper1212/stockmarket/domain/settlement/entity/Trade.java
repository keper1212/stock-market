package com.keper1212.stockmarket.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "trade_event_id", nullable = false, updatable = false)
    private UUID tradeEventId;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "buyer_id", nullable = false)
    private long buyerId;

    @Column(name = "seller_id", nullable = false)
    private long sellerId;

    @Column(name = "buy_order_id")
    private UUID buyOrderId;

    @Column(name = "sell_order_id")
    private UUID sellOrderId;

    @Column(name = "trade_price", nullable = false)
    private long tradePrice;

    @Column(name = "trade_quantity", nullable = false)
    private long tradeQuantity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Trade() {
    }

    private Trade(UUID tradeEventId, String stockCode, long buyerId, long sellerId, UUID buyOrderId, UUID sellOrderId, long tradePrice, long tradeQuantity) {
        this.tradeEventId = tradeEventId;
        this.stockCode = stockCode;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.tradePrice = tradePrice;
        this.tradeQuantity = tradeQuantity;
    }

    public static Trade executed(UUID tradeEventId, String stockCode, long buyerId, long sellerId, UUID buyOrderId, UUID sellOrderId, long tradePrice, long tradeQuantity) {
        return new Trade(tradeEventId, stockCode, buyerId, sellerId, buyOrderId, sellOrderId, tradePrice, tradeQuantity);
    }

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
