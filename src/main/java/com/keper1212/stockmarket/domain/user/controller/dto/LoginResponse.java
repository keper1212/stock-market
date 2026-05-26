package com.keper1212.stockmarket.domain.user.controller.dto;

public record LoginResponse(
        long userId,
        String accessToken,
        String message
) {
    public static LoginResponse success(long userId, String accessToken) {
        return new LoginResponse(userId, accessToken, "로그인에 성공했습니다.");
    }
}
