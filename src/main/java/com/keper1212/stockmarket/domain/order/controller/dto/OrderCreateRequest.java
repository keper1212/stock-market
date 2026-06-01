package com.keper1212.stockmarket.domain.order.controller.dto;

import com.keper1212.stockmarket.domain.order.entity.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderCreateRequest(
        @NotBlank(message = "clientOrderId는 필수입니다.")
        @Size(max = 100, message = "clientOrderId 길이는 100자를 초과할 수 없습니다.")
        String clientOrderId,

        @NotBlank(message = "stockCode는 필수입니다.")
        @Size(max = 20, message = "stockCode 길이는 20자를 초과할 수 없습니다.")
        String stockCode,

        @NotNull(message = "orderType은 필수입니다.")
        OrderType orderType,

        @NotNull(message = "price는 필수입니다.")
        @Min(value = 1, message = "price는 1 이상이어야 합니다.")
        Long price,

        @NotNull(message = "quantity는 필수입니다.")
        @Min(value = 1, message = "quantity는 1 이상이어야 합니다.")
        Long quantity
) {
}
