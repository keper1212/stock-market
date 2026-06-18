package com.keper1212.stockmarket.domain.order.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockOrderValidationRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean isTradableStock(String stockCode) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM stocks
                    WHERE stock_code = ?
                      AND is_trading = TRUE
                ) THEN 1 ELSE 0 END
                """,
                Integer.class,
                stockCode
        );
        return exists != null && exists == 1;
    }
}
