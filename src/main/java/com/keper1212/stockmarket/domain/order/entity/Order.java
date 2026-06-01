package com.keper1212.stockmarket.domain.order.entity;

import com.keper1212.stockmarket.domain.userservice.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "client_order_id", nullable = false, length = 100)
    private String clientOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 4)
    private OrderType orderType;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private long remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "accepted_at", nullable = false)
    private OffsetDateTime acceptedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Order() {
    }

    private Order(
            UUID orderId,
            User user,
            String stockCode,
            String clientOrderId,
            OrderType orderType,
            long price,
            long quantity,
            long remainingQuantity,
            OrderStatus status,
            OffsetDateTime acceptedAt
    ) {
        this.orderId = orderId;
        this.user = user;
        this.stockCode = stockCode;
        this.clientOrderId = clientOrderId;
        this.orderType = orderType;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.acceptedAt = acceptedAt;
    }

    public static Order accept(
            UUID orderId,
            User user,
            String stockCode,
            String clientOrderId,
            OrderType orderType,
            long price,
            long quantity,
            OffsetDateTime acceptedAt
    ) {
        return new Order(
                orderId,
                user,
                stockCode,
                clientOrderId,
                orderType,
                price,
                quantity,
                quantity,
                OrderStatus.ACCEPTED,
                acceptedAt
        );
    }

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (this.acceptedAt == null) {
            this.acceptedAt = now;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }
}
