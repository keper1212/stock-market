package com.keper1212.stockmarket.common.event;

import java.util.UUID;

public record MarketTradeSettledEvent(
        UUID marketEventId,
        String stockCode,
        UUID buyOrderId,
        UUID sellOrderId,
        long tradePrice,
        long tradeQuantity,
        String executedAt
) {
}
