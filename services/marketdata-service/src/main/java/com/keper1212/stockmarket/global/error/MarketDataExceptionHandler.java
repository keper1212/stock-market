package com.keper1212.stockmarket.global.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MarketDataExceptionHandler {

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ErrorResponse> handleOrderException(OrderException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(ErrorResponse.of(e.getMessage()));
    }
}
