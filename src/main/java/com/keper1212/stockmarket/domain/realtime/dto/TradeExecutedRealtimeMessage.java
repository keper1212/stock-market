package com.keper1212.stockmarket.domain.realtime.dto;

import java.util.UUID;

public record TradeExecutedRealtimeMessage(
        String stockCode,
        UUID buyOrderId,
        UUID sellOrderId,
        long tradePrice,
        long tradeQuantity,
        String executedAt
) {
}
