package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderRejectedEvent(
        UUID rejectEventId,
        UUID orderId,
        long userId,
        String stockCode,
        String orderType,
        long price,
        long rejectedQuantity,
        String reason,
        OffsetDateTime rejectedAt
) {
}
