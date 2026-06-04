package com.keper1212.stockmarket.domain.marketdata.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockMarketDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<StockDashboardRow> findDashboardStocks() {
        return jdbcTemplate.query(
                """
                SELECT
                    s.stock_code,
                    s.stock_name,
                    s.base_price,
                    COALESCE(latest.trade_price, s.base_price) AS current_price,
                    COALESCE(today.trade_volume, 0) AS trade_volume,
                    COALESCE(today.trade_amount, 0) AS trade_amount
                FROM stocks s
                LEFT JOIN LATERAL (
                    SELECT t.trade_price
                    FROM trades t
                    WHERE t.stock_code = s.stock_code
                    ORDER BY t.created_at DESC
                    LIMIT 1
                ) latest ON TRUE
                LEFT JOIN (
                    SELECT
                        t.stock_code,
                        SUM(t.trade_quantity)::BIGINT AS trade_volume,
                        SUM(t.trade_price * t.trade_quantity)::BIGINT AS trade_amount
                    FROM trades t
                    WHERE t.created_at >= date_trunc('day', now())
                    GROUP BY t.stock_code
                ) today ON today.stock_code = s.stock_code
                WHERE s.is_trading = TRUE
                ORDER BY s.stock_code
                """,
                (rs, rowNum) -> new StockDashboardRow(
                        rs.getString("stock_code"),
                        rs.getString("stock_name"),
                        rs.getLong("base_price"),
                        rs.getLong("current_price"),
                        rs.getLong("trade_volume"),
                        rs.getLong("trade_amount")
                )
        );
    }

    public record StockDashboardRow(
            String stockCode,
            String stockName,
            long basePrice,
            long currentPrice,
            long tradeVolume,
            long tradeAmount
    ) {
    }
}
