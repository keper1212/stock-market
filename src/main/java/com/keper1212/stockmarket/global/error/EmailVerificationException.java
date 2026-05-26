package com.keper1212.stockmarket.global.error;

import org.springframework.http.HttpStatus;

public class EmailVerificationException extends RuntimeException {

    private final HttpStatus httpStatus;

    public EmailVerificationException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
