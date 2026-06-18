package com.keper1212.stockmarket.domain.order.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderCancelRequest(
        @NotBlank(message = "clientCancelId는 필수입니다.")
        @Size(max = 100, message = "clientCancelId 길이는 100자를 초과할 수 없습니다.")
        String clientCancelId
) {
}
