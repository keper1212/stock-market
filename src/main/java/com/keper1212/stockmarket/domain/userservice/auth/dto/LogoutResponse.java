package com.keper1212.stockmarket.domain.userservice.auth.dto;

public record LogoutResponse(
        String message
) {
    public static LogoutResponse success() {
        return new LogoutResponse("로그아웃되었습니다.");
    }
}
