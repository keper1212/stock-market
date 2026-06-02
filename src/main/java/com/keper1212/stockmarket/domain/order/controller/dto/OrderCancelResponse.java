package com.keper1212.stockmarket.domain.order.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderCancelResponse(
        UUID orderId,
        String message,
        OffsetDateTime acceptedAt
) {
    public static OrderCancelResponse accepted(UUID orderId, OffsetDateTime acceptedAt) {
        return new OrderCancelResponse(
                orderId,
                "주문 취소 요청이 접수되었습니다. 처리 결과를 확인해 주세요.",
                acceptedAt
        );
    }

    public static OrderCancelResponse alreadyAccepted(UUID orderId, OffsetDateTime acceptedAt) {
        return new OrderCancelResponse(
                orderId,
                "이미 접수된 주문 취소 요청입니다. 기존 취소 요청 정보를 반환합니다.",
                acceptedAt
        );
    }
}
