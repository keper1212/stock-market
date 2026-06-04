package com.keper1212.stockmarket.domain.marketdata.controller.dto;

import java.util.List;

public record OrderBookResponse(
        String stockCode,
        long currentPrice,
        List<OrderBookLevelResponse> askOrders,
        List<OrderBookLevelResponse> bidOrders
) {
}
