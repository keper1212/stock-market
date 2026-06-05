package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.controller.dto.OrderHistoryOrderResponse;
import com.keper1212.stockmarket.domain.order.controller.dto.OrderHistoryTradeResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private static final int HISTORY_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public List<OrderHistoryOrderResponse> findMyOrders(long userId) {
        return jdbcTemplate.query(
                """
                SELECT
                    o.order_id,
                    o.stock_code,
                    s.stock_name,
                    o.order_type,
                    o.price,
                    o.quantity,
                    o.remaining_quantity,
                    COALESCE(executions.executed_quantity, 0) AS executed_quantity,
                    o.status,
                    o.accepted_at
                FROM orders o
                JOIN stocks s ON s.stock_code = o.stock_code
                LEFT JOIN (
                    SELECT order_id, SUM(trade_quantity)::BIGINT AS executed_quantity
                    FROM (
                        SELECT buy_order_id AS order_id, trade_quantity
                        FROM trades
                        WHERE buyer_id = ?
                        UNION ALL
                        SELECT sell_order_id AS order_id, trade_quantity
                        FROM trades
                        WHERE seller_id = ?
                    ) user_trades
                    GROUP BY order_id
                ) executions ON executions.order_id = o.order_id
                WHERE o.user_id = ?
                ORDER BY o.accepted_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new OrderHistoryOrderResponse(
                        rs.getObject("order_id", UUID.class),
                        rs.getString("stock_code"),
                        rs.getString("stock_name"),
                        rs.getString("order_type"),
                        rs.getLong("price"),
                        rs.getLong("quantity"),
                        rs.getLong("remaining_quantity"),
                        rs.getLong("executed_quantity"),
                        rs.getString("status"),
                        rs.getObject("accepted_at", OffsetDateTime.class)
                ),
                userId,
                userId,
                userId,
                HISTORY_LIMIT
        );
    }

    public List<OrderHistoryTradeResponse> findMyTrades(long userId) {
        return jdbcTemplate.query(
                """
                SELECT
                    t.trade_id,
                    t.stock_code,
                    s.stock_name,
                    CASE WHEN t.buyer_id = ? THEN 'BUY' ELSE 'SELL' END AS order_type,
                    CASE WHEN t.buyer_id = ? THEN t.buy_order_id ELSE t.sell_order_id END AS order_id,
                    t.trade_price,
                    t.trade_quantity,
                    (t.trade_price * t.trade_quantity) AS trade_amount,
                    t.created_at
                FROM trades t
                JOIN stocks s ON s.stock_code = t.stock_code
                WHERE t.buyer_id = ? OR t.seller_id = ?
                ORDER BY t.created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new OrderHistoryTradeResponse(
                        rs.getLong("trade_id"),
                        rs.getString("stock_code"),
                        rs.getString("stock_name"),
                        rs.getString("order_type"),
                        rs.getObject("order_id", UUID.class),
                        rs.getLong("trade_price"),
                        rs.getLong("trade_quantity"),
                        rs.getLong("trade_amount"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                userId,
                userId,
                userId,
                userId,
                HISTORY_LIMIT
        );
    }
}
