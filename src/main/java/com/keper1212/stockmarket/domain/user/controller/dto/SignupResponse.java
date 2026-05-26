package com.keper1212.stockmarket.domain.user.controller.dto;

public record SignupResponse(
        long userId,
        String message
) {
    public static SignupResponse created(long userId) {
        return new SignupResponse(
                userId,
                "회원가입이 완료되었습니다. 가상 예수금 1,000만 원이 지급되었습니다."
        );
    }
}
