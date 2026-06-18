package com.keper1212.stockmarket.global.error;

public record ErrorResponse(
        String message
) {
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message);
    }
}
