package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderCanceledEvent(
        UUID cancelEventId,
        UUID orderId,
        long userId,
        String stockCode,
        String orderType,
        long price,
        long canceledQuantity,
        String clientCancelId,
        OffsetDateTime canceledAt
) {
}
