package com.keper1212.stockmarket.domain.userservice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "email 길이는 100자를 초과할 수 없습니다.")
        String email,

        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 100, message = "password는 8자 이상 100자 이하여야 합니다.")
        String password,

        @Size(max = 50, message = "name 길이는 50자를 초과할 수 없습니다.")
        String name
) {
}
