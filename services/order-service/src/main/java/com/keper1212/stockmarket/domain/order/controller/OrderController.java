package com.keper1212.stockmarket.domain.order.controller;

import com.keper1212.stockmarket.common.security.CurrentUser;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderHistoryResponse;
import com.keper1212.stockmarket.domain.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/me")
    public ResponseEntity<OrderHistoryResponse> getMyOrderHistory(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(orderService.getMyOrderHistory(currentUser.userId()));
    }

    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        OrderCreateResponse response = orderService.placeOrder(currentUser.userId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderCancelResponse> cancelOrder(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody OrderCancelRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        OrderCancelResponse response = orderService.cancelOrder(currentUser.userId(), orderId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
