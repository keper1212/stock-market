package com.keper1212.stockmarket.domain.order.entity;

public enum OrderStatus {
    ACCEPTED,
    PARTIALLY_FILLED,
    CANCEL_REQUESTED,
    FILLED,
    CANCELED,
    REJECTED
}
