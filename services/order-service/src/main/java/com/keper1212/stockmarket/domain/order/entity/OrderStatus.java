package com.keper1212.stockmarket.domain.order.entity;

public enum OrderStatus {
    PENDING_ASSET_HOLD,
    ACCEPTED,
    PARTIALLY_FILLED,
    CANCEL_REQUESTED,
    FILLED,
    CANCELED,
    REJECTED
}
