package com.keper1212.stockmarket.domain.user.controller.dto;

public record EmailCodeResponse(
        String message,
        long expiresInSeconds
) {
    public static EmailCodeResponse sent(long expiresInSeconds) {
        return new EmailCodeResponse(
                "인증번호가 이메일로 발송되었습니다.",
                expiresInSeconds
        );
    }
}
