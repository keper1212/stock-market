package com.keper1212.stockmarket.domain.order.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderHistoryTradeResponse(
        Long tradeId,
        String stockCode,
        String stockName,
        String orderType,
        UUID orderId,
        long tradePrice,
        long tradeQuantity,
        long tradeAmount,
        OffsetDateTime executedAt
) {
}
