package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeExecutedEvent(
        UUID tradeEventId,
        String stockCode,
        UUID buyOrderId,
        long buyerId,
        long buyOrderPrice,
        long buyOrderRemaining,
        String buyOrderStatus,
        UUID sellOrderId,
        long sellerId,
        long sellOrderPrice,
        long sellOrderRemaining,
        String sellOrderStatus,
        long tradePrice,
        long tradeQuantity,
        OffsetDateTime executedAt
) {
}
