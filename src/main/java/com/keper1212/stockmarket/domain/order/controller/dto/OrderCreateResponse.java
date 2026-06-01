package com.keper1212.stockmarket.domain.order.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderCreateResponse(
        UUID orderId,
        String message,
        OffsetDateTime acceptedAt
) {
    public static OrderCreateResponse accepted(UUID orderId, OffsetDateTime acceptedAt) {
        return new OrderCreateResponse(
                orderId,
                "주문이 정상적으로 접수되었습니다. 체결 결과를 기다려주세요.",
                acceptedAt
        );
    }

    public static OrderCreateResponse alreadyAccepted(UUID orderId, OffsetDateTime acceptedAt) {
        return new OrderCreateResponse(
                orderId,
                "이미 접수된 주문입니다. 기존 주문 정보를 반환합니다.",
                acceptedAt
        );
    }
}
