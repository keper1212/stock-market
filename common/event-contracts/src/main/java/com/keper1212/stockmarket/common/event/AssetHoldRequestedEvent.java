package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetHoldRequestedEvent(
        UUID requestId,
        UUID orderId,
        long userId,
        String stockCode,
        String orderType,
        long price,
        long quantity,
        String clientOrderId,
        OffsetDateTime requestedAt
) {
}
