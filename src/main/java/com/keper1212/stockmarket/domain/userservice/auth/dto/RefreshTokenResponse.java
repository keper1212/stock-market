package com.keper1212.stockmarket.domain.userservice.auth.dto;

public record RefreshTokenResponse(
        String accessToken,
        String message
) {
    public static RefreshTokenResponse success(String accessToken) {
        return new RefreshTokenResponse(accessToken, "토큰이 성공적으로 재발급되었습니다.");
    }
}
