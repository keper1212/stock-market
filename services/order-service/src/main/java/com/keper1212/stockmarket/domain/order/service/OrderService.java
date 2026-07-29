package com.keper1212.stockmarket.domain.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.common.event.EventTypes;
import com.keper1212.stockmarket.common.event.KafkaTopics;
import com.keper1212.stockmarket.common.event.AssetHoldRequestedEvent;
import com.keper1212.stockmarket.common.event.OrderCancelRequestedEvent;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCancelResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateRequest;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderCreateResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderHistoryResponse;
import com.keper1212.stockmarket.domain.order.entity.Order;
import com.keper1212.stockmarket.domain.order.entity.OrderStatus;
import com.keper1212.stockmarket.domain.order.entity.OrderType;
import com.keper1212.stockmarket.domain.order.entity.OrderOutboxEvent;
import com.keper1212.stockmarket.domain.order.repository.OrderRepository;
import com.keper1212.stockmarket.domain.order.repository.OrderQueryRepository;
import com.keper1212.stockmarket.domain.order.repository.OrderOutboxEventRepository;
import com.keper1212.stockmarket.domain.order.repository.StockOrderValidationRepository;
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

    private static final String ASSET_COMMAND_TOPIC = KafkaTopics.ASSET_COMMANDS;
    private static final String ORDER_EVENT_TOPIC = KafkaTopics.ORDER_EVENTS;
    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String EVENT_TYPE_ASSET_HOLD_REQUESTED = EventTypes.ASSET_HOLD_REQUESTED;
    private static final String EVENT_TYPE_ORDER_CANCEL_REQUESTED = EventTypes.ORDER_CANCEL_REQUESTED;

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderOutboxEventRepository outboxEventRepository;
    private final StockOrderValidationRepository stockOrderValidationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public OrderHistoryResponse getMyOrderHistory(Long userId) {
        return new OrderHistoryResponse(
                orderQueryRepository.findMyOrders(userId),
                orderQueryRepository.findMyTrades(userId)
        );
    }

    @Transactional
    public OrderCreateResponse placeOrder(Long userId, OrderCreateRequest request) {
        String clientOrderId = normalizeClientOrderId(request.clientOrderId());

        Order existingOrder = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId).orElse(null);
        if (existingOrder != null) {
            return OrderCreateResponse.alreadyAccepted(existingOrder.getOrderId(), existingOrder.getAcceptedAt());
        }

        String stockCode = normalizeStockCode(request.stockCode());
        if (!stockOrderValidationRepository.isTradableStock(stockCode)) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "거래 가능한 종목이 아닙니다.");
        }

        long price = request.price();
        long quantity = request.quantity();

        OffsetDateTime acceptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        UUID orderId = UUID.randomUUID();

        Order order = Order.pendingAssetHold(
                orderId,
                userId,
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
            Order duplicatedOrder = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId)
                    .orElseThrow(() -> new OrderException(HttpStatus.CONFLICT, "중복 주문 처리 중 충돌이 발생했습니다."));
            return OrderCreateResponse.alreadyAccepted(duplicatedOrder.getOrderId(), duplicatedOrder.getAcceptedAt());
        }

        UUID holdRequestId = UUID.randomUUID();
        JsonNode payload = objectMapper.valueToTree(new AssetHoldRequestedEvent(
                holdRequestId,
                orderId,
                userId,
                stockCode,
                request.orderType().name(),
                price,
                quantity,
                clientOrderId,
                acceptedAt
        ));

        OrderOutboxEvent outboxEvent = OrderOutboxEvent.pending(
                holdRequestId,
                AGGREGATE_TYPE_ORDER,
                orderId.toString(),
                EVENT_TYPE_ASSET_HOLD_REQUESTED,
                ASSET_COMMAND_TOPIC,
                orderId.toString(),
                payload
        );
        outboxEventRepository.save(outboxEvent);

        return OrderCreateResponse.accepted(orderId, acceptedAt);
    }

    @Transactional
    public OrderCancelResponse cancelOrder(Long userId, UUID orderId, OrderCancelRequest request) {
        String clientCancelId = normalizeClientCancelId(request.clientCancelId());
        Order order = orderRepository.findByOrderIdAndUserId(orderId, userId)
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

        JsonNode payload = objectMapper.valueToTree(new OrderCancelRequestedEvent(
                orderId,
                userId,
                order.getStockCode(),
                clientCancelId,
                cancelRequestedAt
        ));

        OrderOutboxEvent outboxEvent = OrderOutboxEvent.pending(
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

    private String normalizeStockCode(String stockCode) {
        return stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeClientOrderId(String clientOrderId) {
        return clientOrderId.trim();
    }

    private String normalizeClientCancelId(String clientCancelId) {
        return clientCancelId.trim();
    }


}
