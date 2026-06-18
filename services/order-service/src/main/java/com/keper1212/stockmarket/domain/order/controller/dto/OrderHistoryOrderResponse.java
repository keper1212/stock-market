package com.keper1212.stockmarket.domain.order.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderHistoryOrderResponse(
        UUID orderId,
        String stockCode,
        String stockName,
        String orderType,
        long price,
        long quantity,
        long remainingQuantity,
        long executedQuantity,
        String status,
        OffsetDateTime acceptedAt
) {
}
