package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderCancelRequestedEvent(
        UUID orderId,
        Long userId,
        String stockCode,
        String clientCancelId,
        OffsetDateTime cancelRequestedAt
) {
}
