package com.keper1212.stockmarket.domain.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateResponse;
import com.keper1212.stockmarket.domain.order.entity.Order;
import com.keper1212.stockmarket.domain.order.entity.OrderStatus;
import com.keper1212.stockmarket.domain.order.entity.OrderType;
import com.keper1212.stockmarket.domain.order.entity.OutboxEvent;
import com.keper1212.stockmarket.domain.order.repository.OrderRepository;
import com.keper1212.stockmarket.domain.order.repository.OutboxEventRepository;
import com.keper1212.stockmarket.domain.order.repository.StockOrderValidationRepository;
import com.keper1212.stockmarket.domain.userservice.entity.User;
import com.keper1212.stockmarket.domain.userservice.repository.AccountRepository;
import com.keper1212.stockmarket.domain.userservice.repository.UserRepository;
import com.keper1212.stockmarket.domain.userservice.repository.UserStockRepository;
import com.keper1212.stockmarket.global.error.AuthException;
import com.keper1212.stockmarket.global.error.OrderException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_EVENT_TOPIC = "order-events";
    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String EVENT_TYPE_ORDER_ACCEPTED = "ORDER_ACCEPTED";
    private static final String EVENT_TYPE_ORDER_CANCEL_REQUESTED = "ORDER_CANCEL_REQUESTED";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserStockRepository userStockRepository;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StockOrderValidationRepository stockOrderValidationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderCreateResponse placeOrder(Long userId, OrderCreateRequest request) {
        String clientOrderId = normalizeClientOrderId(request.clientOrderId());

        Order existingOrder = orderRepository.findByUser_UserIdAndClientOrderId(userId, clientOrderId).orElse(null);
        if (existingOrder != null) {
            return OrderCreateResponse.alreadyAccepted(existingOrder.getOrderId(), existingOrder.getAcceptedAt());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "사용자 정보가 존재하지 않습니다."));

        String stockCode = normalizeStockCode(request.stockCode());
        if (!stockOrderValidationRepository.isTradableStock(stockCode)) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "거래 가능한 종목이 아닙니다.");
        }

        long price = request.price();
        long quantity = request.quantity();

        reserveResources(userId, request.orderType(), stockCode, price, quantity);

        OffsetDateTime acceptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        UUID orderId = UUID.randomUUID();

        Order order = Order.accept(
                orderId,
                user,
                stockCode,
                clientOrderId,
                request.orderType(),
                price,
                quantity,
                acceptedAt
        );
        try {
            orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            Order duplicatedOrder = orderRepository.findByUser_UserIdAndClientOrderId(userId, clientOrderId)
                    .orElseThrow(() -> new OrderException(HttpStatus.CONFLICT, "중복 주문 처리 중 충돌이 발생했습니다."));
            return OrderCreateResponse.alreadyAccepted(duplicatedOrder.getOrderId(), duplicatedOrder.getAcceptedAt());
        }

        JsonNode payload = objectMapper.valueToTree(new OrderAcceptedOutboxPayload(
                orderId,
                userId,
                stockCode,
                request.orderType().name(),
                price,
                quantity,
                quantity,
                clientOrderId,
                acceptedAt
        ));

        OutboxEvent outboxEvent = OutboxEvent.pending(
                UUID.randomUUID(),
                AGGREGATE_TYPE_ORDER,
                orderId.toString(),
                EVENT_TYPE_ORDER_ACCEPTED,
                ORDER_EVENT_TOPIC,
                stockCode,
                payload
        );
        outboxEventRepository.save(outboxEvent);

        return OrderCreateResponse.accepted(orderId, acceptedAt);
    }

    @Transactional
    public OrderCancelResponse cancelOrder(Long userId, UUID orderId, OrderCancelRequest request) {
        String clientCancelId = normalizeClientCancelId(request.clientCancelId());
        Order order = orderRepository.findByOrderIdAndUser_UserId(orderId, userId)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, "취소할 주문이 존재하지 않습니다."));

        if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            if (clientCancelId.equals(order.getCancelClientId())) {
                return OrderCancelResponse.alreadyAccepted(orderId, order.getCancelRequestedAt());
            }
            throw new OrderException(HttpStatus.CONFLICT, "이미 다른 취소 요청이 접수된 주문입니다.");
        }

        OffsetDateTime cancelRequestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int updatedRows = orderRepository.requestCancelIfCancelable(orderId, userId, clientCancelId, cancelRequestedAt);
        if (updatedRows == 0) {
            throw new OrderException(HttpStatus.CONFLICT, "이미 체결되었거나 취소할 수 없는 주문입니다.");
        }

        JsonNode payload = objectMapper.valueToTree(new OrderCancelRequestedOutboxPayload(
                orderId,
                userId,
                order.getStockCode(),
                clientCancelId,
                cancelRequestedAt
        ));

        OutboxEvent outboxEvent = OutboxEvent.pending(
                UUID.randomUUID(),
                AGGREGATE_TYPE_ORDER,
                orderId.toString(),
                EVENT_TYPE_ORDER_CANCEL_REQUESTED,
                ORDER_EVENT_TOPIC,
                order.getStockCode(),
                payload
        );
        outboxEventRepository.save(outboxEvent);

        return OrderCancelResponse.accepted(orderId, cancelRequestedAt);
    }

    private void reserveResources(Long userId, OrderType orderType, String stockCode, long price, long quantity) {
        if (orderType == OrderType.BUY) {
            if (!accountRepository.existsByUser_UserId(userId)) {
                throw new OrderException(HttpStatus.NOT_FOUND, "계좌 정보가 존재하지 않습니다.");
            }

            long lockAmount = multiplyExact(price, quantity);
            int updatedRows = accountRepository.lockCashByUserIdIfAvailable(userId, lockAmount);
            if (updatedRows == 0) {
                throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY, "예수금이 부족합니다.");
            }
            return;
        }

        int updatedRows = userStockRepository.lockQuantityByUserIdAndStockCodeIfAvailable(userId, stockCode, quantity);
        if (updatedRows == 0) {
            throw new OrderException(HttpStatus.UNPROCESSABLE_ENTITY, "보유 수량이 부족합니다.");
        }
    }

    private long multiplyExact(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "주문 금액이 허용 범위를 초과했습니다.");
        }
    }

    private String normalizeStockCode(String stockCode) {
        return stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeClientOrderId(String clientOrderId) {
        return clientOrderId.trim();
    }

    private String normalizeClientCancelId(String clientCancelId) {
        return clientCancelId.trim();
    }

    private record OrderAcceptedOutboxPayload(
            UUID orderId,
            Long userId,
            String stockCode,
            String orderType,
            long price,
            long quantity,
            long remainingQuantity,
            String clientOrderId,
            OffsetDateTime acceptedAt
    ) {
    }

    private record OrderCancelRequestedOutboxPayload(
            UUID orderId,
            Long userId,
            String stockCode,
            String clientCancelId,
            OffsetDateTime cancelRequestedAt
    ) {
    }
}
