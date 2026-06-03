package com.keper1212.stockmarket.domain.matching.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderBookService {

    private static final String BID = "BID";
    private static final String ASK = "ASK";
    private static final String ORDER_STATUS_ACCEPTED = "ACCEPTED";
    private static final String ORDER_STATUS_CANCEL_REQUESTED = "CANCEL_REQUESTED";

    private static final String BID_KEY_PREFIX = "orderbook:bid:";
    private static final String ASK_KEY_PREFIX = "orderbook:ask:";
    private static final String VOLUME_KEY_PREFIX = "orderbook:volume:";
    private static final String ORDERS_KEY_PREFIX = "orderbook:orders:";
    private static final String ORDER_KEY_PREFIX = "order:";

    private static final DefaultRedisScript<Long> STORE_ACCEPTED_ORDER_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[4]) == 1 then
                return 0
            end

            redis.call('HSET', KEYS[4],
                'orderId', ARGV[1],
                'userId', ARGV[2],
                'stockCode', ARGV[3],
                'orderType', ARGV[4],
                'side', ARGV[5],
                'price', ARGV[6],
                'quantity', ARGV[7],
                'remainingQuantity', ARGV[8],
                'status', ARGV[9],
                'clientOrderId', ARGV[10],
                'acceptedAt', ARGV[11]
            )

            redis.call('ZADD', KEYS[1], ARGV[6], ARGV[6])
            redis.call('HINCRBY', KEYS[2], ARGV[12], ARGV[8])
            redis.call('RPUSH', KEYS[3], ARGV[1])

            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> MARK_CANCEL_REQUESTED_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end

            redis.call('HSET', KEYS[1],
                'status', ARGV[1],
                'clientCancelId', ARGV[2],
                'cancelRequestedAt', ARGV[3]
            )

            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public boolean storeAcceptedOrder(JsonNode payload) {
        String orderId = requiredText(payload, "orderId");
        String userId = requiredText(payload, "userId");
        String stockCode = requiredText(payload, "stockCode");
        String orderType = requiredText(payload, "orderType");
        String price = requiredText(payload, "price");
        String quantity = requiredText(payload, "quantity");
        String remainingQuantity = requiredText(payload, "remainingQuantity");
        String clientOrderId = requiredText(payload, "clientOrderId");
        String acceptedAt = requiredText(payload, "acceptedAt");

        String side = resolveSide(orderType);
        String priceBookKey = priceBookKey(stockCode, side);
        String volumeKey = VOLUME_KEY_PREFIX + stockCode;
        String volumeField = side + ":" + price;
        String ordersKey = ORDERS_KEY_PREFIX + stockCode + ":" + side + ":" + price;
        String orderKey = ORDER_KEY_PREFIX + orderId;

        Long result = stringRedisTemplate.execute(
                STORE_ACCEPTED_ORDER_SCRIPT,
                List.of(priceBookKey, volumeKey, ordersKey, orderKey),
                orderId,
                userId,
                stockCode,
                orderType,
                side,
                price,
                quantity,
                remainingQuantity,
                ORDER_STATUS_ACCEPTED,
                clientOrderId,
                acceptedAt,
                volumeField
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean markCancelRequested(JsonNode payload) {
        String orderId = requiredText(payload, "orderId");
        String clientCancelId = requiredText(payload, "clientCancelId");
        String cancelRequestedAt = requiredText(payload, "cancelRequestedAt");

        Long result = stringRedisTemplate.execute(
                MARK_CANCEL_REQUESTED_SCRIPT,
                List.of(ORDER_KEY_PREFIX + orderId),
                ORDER_STATUS_CANCEL_REQUESTED,
                clientCancelId,
                cancelRequestedAt
        );

        return Long.valueOf(1L).equals(result);
    }

    private String priceBookKey(String stockCode, String side) {
        if (BID.equals(side)) {
            return BID_KEY_PREFIX + stockCode;
        }
        return ASK_KEY_PREFIX + stockCode;
    }

    private String resolveSide(String orderType) {
        if ("BUY".equals(orderType)) {
            return BID;
        }
        if ("SELL".equals(orderType)) {
            return ASK;
        }
        throw new IllegalArgumentException("Unsupported orderType: " + orderType);
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing order event payload field: " + fieldName);
        }
        return value.asText();
    }
}
