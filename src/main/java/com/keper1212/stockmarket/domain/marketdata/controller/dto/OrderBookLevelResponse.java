package com.keper1212.stockmarket.domain.marketdata.controller.dto;

public record OrderBookLevelResponse(
        long price,
        long quantity
) {
}
