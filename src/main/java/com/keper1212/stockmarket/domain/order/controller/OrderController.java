package com.keper1212.stockmarket.domain.order.controller;

import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateResponse;
import com.keper1212.stockmarket.domain.order.service.OrderService;
import com.keper1212.stockmarket.global.error.AuthException;
import com.keper1212.stockmarket.global.util.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = extractUserId(authorization);
        OrderCreateResponse response = orderService.placeOrder(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderCancelResponse> cancelOrder(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody OrderCancelRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = extractUserId(authorization);
        OrderCancelResponse response = orderService.cancelOrder(userId, orderId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private Long extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Access Token이 필요합니다.");
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Access Token이 필요합니다.");
        }

        try {
            return jwtTokenProvider.getUserIdFromToken(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Access Token이 유효하지 않습니다.");
        }
    }
}
