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
    private static final String MATCH_RESULT_KEY_PREFIX = "match:results:";
    private static final String ORDER_MATCH_RESULT_KEY_PREFIX = "match:order:";

    private static final String BID_KEY_PREFIX = "orderbook:bid:";
    private static final String ASK_KEY_PREFIX = "orderbook:ask:";
    private static final String VOLUME_KEY_PREFIX = "orderbook:volume:";
    private static final String ORDERS_KEY_PREFIX = "orderbook:orders:";
    private static final String ORDER_KEY_PREFIX = "order:";

    private static final DefaultRedisScript<String> MATCH_ACCEPTED_ORDER_SCRIPT = new DefaultRedisScript<>(
            """
            local function cleanup_price_level(volumeKey, priceBookKey, volumeField, price)
                local currentVolume = tonumber(redis.call('HGET', volumeKey, volumeField) or '0')
                if currentVolume <= 0 then
                    redis.call('HDEL', volumeKey, volumeField)
                    redis.call('ZREM', priceBookKey, price)
                end
            end

            local function enqueue_remaining(priceBookKey, volumeKey, ordersKey, volumeField, price, orderId, remainingQuantity)
                redis.call('ZADD', priceBookKey, price, price)
                redis.call('HINCRBY', volumeKey, volumeField, remainingQuantity)
                redis.call('RPUSH', ordersKey, orderId)
            end

            if redis.call('EXISTS', KEYS[3]) == 1 then
                local existingTrades = redis.call('LRANGE', KEYS[6], 0, -1)
                local trades = {}
                local totalMatchedQuantity = 0
                for i, tradeJson in ipairs(existingTrades) do
                    trades[i] = cjson.decode(tradeJson)
                    totalMatchedQuantity = totalMatchedQuantity + tonumber(trades[i]['tradeQuantity'] or 0)
                end

                return cjson.encode({
                    result = 'DUPLICATE',
                    orderId = ARGV[1],
                    status = redis.call('HGET', KEYS[3], 'status'),
                    matchedCount = #trades,
                    totalMatchedQuantity = totalMatchedQuantity,
                    remainingQuantity = tonumber(redis.call('HGET', KEYS[3], 'remainingQuantity') or '0'),
                    trades = trades
                })
            end

            local orderId = ARGV[1]
            local userId = ARGV[2]
            local stockCode = ARGV[3]
            local orderType = ARGV[4]
            local side = ARGV[5]
            local oppositeSide = ARGV[6]
            local price = tonumber(ARGV[7])
            local priceText = ARGV[7]
            local quantity = tonumber(ARGV[8])
            local remainingQuantity = tonumber(ARGV[9])
            local status = ARGV[10]
            local clientOrderId = ARGV[11]
            local acceptedAt = ARGV[12]
            local ownVolumeField = ARGV[13]
            local ownOrdersKey = ARGV[14]
            local ordersKeyPrefix = ARGV[15]

            redis.call('HSET', KEYS[3],
                'orderId', ARGV[1],
                'userId', ARGV[2],
                'stockCode', ARGV[3],
                'orderType', ARGV[4],
                'side', ARGV[5],
                'price', ARGV[7],
                'quantity', ARGV[8],
                'remainingQuantity', ARGV[9],
                'status', ARGV[10],
                'clientOrderId', ARGV[11],
                'acceptedAt', ARGV[12]
            )

            local matchedCount = 0
            local totalMatchedQuantity = 0
            local trades = {}

            while remainingQuantity > 0 do
                local bestPriceRows
                if orderType == 'BUY' then
                    bestPriceRows = redis.call('ZRANGE', KEYS[2], 0, 0)
                else
                    bestPriceRows = redis.call('ZREVRANGE', KEYS[2], 0, 0)
                end

                if #bestPriceRows == 0 then
                    break
                end

                local oppositePriceText = bestPriceRows[1]
                local oppositePrice = tonumber(oppositePriceText)

                if orderType == 'BUY' and oppositePrice > price then
                    break
                end
                if orderType == 'SELL' and oppositePrice < price then
                    break
                end

                local oppositeVolumeField = oppositeSide .. ':' .. oppositePriceText
                local oppositeOrdersKey = ordersKeyPrefix .. oppositeSide .. ':' .. oppositePriceText
                local oppositeOrderId = redis.call('LINDEX', oppositeOrdersKey, 0)

                if not oppositeOrderId then
                    redis.call('HDEL', KEYS[4], oppositeVolumeField)
                    redis.call('ZREM', KEYS[2], oppositePriceText)
                else
                    local oppositeOrderKey = 'order:' .. oppositeOrderId
                    local oppositeRemaining = tonumber(redis.call('HGET', oppositeOrderKey, 'remainingQuantity') or '0')
                    local oppositeStatus = redis.call('HGET', oppositeOrderKey, 'status')

                    if oppositeRemaining <= 0 or oppositeStatus == 'CANCEL_REQUESTED' or oppositeStatus == 'CANCELED' then
                        redis.call('LPOP', oppositeOrdersKey)
                        if oppositeRemaining > 0 then
                            redis.call('HINCRBY', KEYS[4], oppositeVolumeField, -oppositeRemaining)
                            redis.call('HSET', oppositeOrderKey, 'remainingQuantity', 0, 'status', 'CANCELED')
                        end
                        cleanup_price_level(KEYS[4], KEYS[2], oppositeVolumeField, oppositePriceText)
                    else
                        local tradeQuantity = remainingQuantity
                        if oppositeRemaining < tradeQuantity then
                            tradeQuantity = oppositeRemaining
                        end

                        remainingQuantity = remainingQuantity - tradeQuantity
                        oppositeRemaining = oppositeRemaining - tradeQuantity
                        matchedCount = matchedCount + 1
                        totalMatchedQuantity = totalMatchedQuantity + tradeQuantity

                        if oppositeRemaining == 0 then
                            redis.call('HSET', oppositeOrderKey, 'remainingQuantity', 0, 'status', 'FILLED')
                            redis.call('LPOP', oppositeOrdersKey)
                        else
                            redis.call('HSET', oppositeOrderKey, 'remainingQuantity', oppositeRemaining, 'status', 'PARTIALLY_FILLED')
                        end

                        redis.call('HINCRBY', KEYS[4], oppositeVolumeField, -tradeQuantity)
                        cleanup_price_level(KEYS[4], KEYS[2], oppositeVolumeField, oppositePriceText)

                        local buyOrderId = orderId
                        local buyerId = userId
                        local buyOrderPrice = price
                        local buyOrderRemaining = remainingQuantity
                        local buyOrderStatus = 'ACCEPTED'
                        local sellOrderId = oppositeOrderId
                        local sellerId = redis.call('HGET', oppositeOrderKey, 'userId')
                        local sellOrderPrice = tonumber(redis.call('HGET', oppositeOrderKey, 'price'))
                        local sellOrderRemaining = oppositeRemaining
                        local sellOrderStatus = redis.call('HGET', oppositeOrderKey, 'status')
                        if orderType == 'SELL' then
                            buyOrderId = oppositeOrderId
                            buyerId = redis.call('HGET', oppositeOrderKey, 'userId')
                            buyOrderPrice = tonumber(redis.call('HGET', oppositeOrderKey, 'price'))
                            buyOrderRemaining = oppositeRemaining
                            buyOrderStatus = redis.call('HGET', oppositeOrderKey, 'status')
                            sellOrderId = orderId
                            sellerId = userId
                            sellOrderPrice = price
                            sellOrderRemaining = remainingQuantity
                            sellOrderStatus = 'ACCEPTED'
                        end

                        if buyOrderRemaining == 0 then
                            buyOrderStatus = 'FILLED'
                        elseif orderType == 'SELL' or buyOrderRemaining < tonumber(redis.call('HGET', 'order:' .. buyOrderId, 'quantity') or '0') then
                            buyOrderStatus = 'PARTIALLY_FILLED'
                        end

                        if sellOrderRemaining == 0 then
                            sellOrderStatus = 'FILLED'
                        elseif orderType == 'BUY' or sellOrderRemaining < tonumber(redis.call('HGET', 'order:' .. sellOrderId, 'quantity') or '0') then
                            sellOrderStatus = 'PARTIALLY_FILLED'
                        end

                        local tradeJson = cjson.encode({
                            stockCode = stockCode,
                            buyOrderId = buyOrderId,
                            buyerId = buyerId,
                            buyOrderPrice = buyOrderPrice,
                            buyOrderRemaining = buyOrderRemaining,
                            buyOrderStatus = buyOrderStatus,
                            sellOrderId = sellOrderId,
                            sellerId = sellerId,
                            sellOrderPrice = sellOrderPrice,
                            sellOrderRemaining = sellOrderRemaining,
                            sellOrderStatus = sellOrderStatus,
                            tradePrice = oppositePrice,
                            tradeQuantity = tradeQuantity
                        })

                        redis.call('RPUSH', KEYS[5], tradeJson)
                        redis.call('RPUSH', KEYS[6], tradeJson)
                        trades[matchedCount] = cjson.decode(tradeJson)
                    end
                end
            end

            local finalStatus = 'ACCEPTED'
            if remainingQuantity == 0 then
                finalStatus = 'FILLED'
            elseif remainingQuantity < quantity then
                finalStatus = 'PARTIALLY_FILLED'
            end

            redis.call('HSET', KEYS[3], 'remainingQuantity', remainingQuantity, 'status', finalStatus)

            if remainingQuantity > 0 then
                enqueue_remaining(KEYS[1], KEYS[4], ownOrdersKey, ownVolumeField, priceText, orderId, remainingQuantity)
            end

            return cjson.encode({
                result = 'MATCHED',
                orderId = orderId,
                status = finalStatus,
                matchedCount = matchedCount,
                totalMatchedQuantity = totalMatchedQuantity,
                remainingQuantity = remainingQuantity,
                trades = trades
            })
            """,
            String.class
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

    public String matchAcceptedOrder(JsonNode payload) {
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
        String oppositeSide = oppositeSide(side);
        String priceBookKey = priceBookKey(stockCode, side);
        String oppositePriceBookKey = priceBookKey(stockCode, oppositeSide);
        String volumeKey = VOLUME_KEY_PREFIX + stockCode;
        String volumeField = side + ":" + price;
        String ordersKey = ORDERS_KEY_PREFIX + stockCode + ":" + side + ":" + price;
        String orderKey = ORDER_KEY_PREFIX + orderId;
        String matchResultKey = MATCH_RESULT_KEY_PREFIX + stockCode;
        String ordersKeyPrefix = ORDERS_KEY_PREFIX + stockCode + ":";

        return stringRedisTemplate.execute(
                MATCH_ACCEPTED_ORDER_SCRIPT,
                List.of(priceBookKey, oppositePriceBookKey, orderKey, volumeKey, matchResultKey, ORDER_MATCH_RESULT_KEY_PREFIX + orderId),
                orderId,
                userId,
                stockCode,
                orderType,
                side,
                oppositeSide,
                price,
                quantity,
                remainingQuantity,
                ORDER_STATUS_ACCEPTED,
                clientOrderId,
                acceptedAt,
                volumeField,
                ordersKey,
                ordersKeyPrefix
        );
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

    private String oppositeSide(String side) {
        if (BID.equals(side)) {
            return ASK;
        }
        return BID;
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing order event payload field: " + fieldName);
        }
        return value.asText();
    }
}
