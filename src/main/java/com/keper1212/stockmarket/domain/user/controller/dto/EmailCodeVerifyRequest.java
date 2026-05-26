package com.keper1212.stockmarket.domain.user.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailCodeVerifyRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "email 길이는 100자를 초과할 수 없습니다.")
        String email,

        @NotBlank(message = "code는 필수입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "code는 6자리 숫자여야 합니다.")
        String code
) {
}
