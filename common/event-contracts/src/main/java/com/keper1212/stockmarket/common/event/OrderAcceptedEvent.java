package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderAcceptedEvent(
        UUID orderId,
        Long userId,
        String stockCode,
        String orderType,
        long price,
        long quantity,
        long remainingQuantity,
        String clientOrderId,
        OffsetDateTime acceptedAt
) {
}
