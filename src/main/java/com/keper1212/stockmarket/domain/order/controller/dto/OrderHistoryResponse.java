package com.keper1212.stockmarket.domain.order.controller.dto;

import java.util.List;

public record OrderHistoryResponse(
        List<OrderHistoryOrderResponse> orders,
        List<OrderHistoryTradeResponse> trades
) {
}
