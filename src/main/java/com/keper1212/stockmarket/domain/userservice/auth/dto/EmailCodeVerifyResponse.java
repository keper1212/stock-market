package com.keper1212.stockmarket.domain.userservice.auth.dto;

public record EmailCodeVerifyResponse(
        String message
) {
    public static EmailCodeVerifyResponse verified() {
        return new EmailCodeVerifyResponse("이메일 인증이 완료되었습니다.");
    }
}
