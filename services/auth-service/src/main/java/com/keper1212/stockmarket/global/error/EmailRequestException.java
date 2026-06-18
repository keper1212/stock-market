package com.keper1212.stockmarket.global.error;

public class EmailRequestException extends RuntimeException {

    public EmailRequestException(String message) {
        super(message);
    }
}
