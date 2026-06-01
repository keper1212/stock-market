package com.keper1212.stockmarket.domain.userservice.repository;

import com.keper1212.stockmarket.domain.userservice.entity.UserStock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserStockRepository extends JpaRepository<UserStock, Long> {

    @Query(value = """
            SELECT
                us.stock_code AS stockCode,
                s.stock_name AS stockName,
                us.quantity AS quantity,
                us.average_cost AS averageCost,
                s.base_price AS basePrice
            FROM user_stocks us
            JOIN stocks s ON s.stock_code = us.stock_code
            WHERE us.user_id = :userId
              AND us.quantity > 0
            ORDER BY us.stock_code
            """, nativeQuery = true)
    List<UserStockAssetView> findHoldingStocksByUserId(@Param("userId") Long userId);

    interface UserStockAssetView {
        String getStockCode();

        String getStockName();

        long getQuantity();

        double getAverageCost();

        long getBasePrice();
    }
}
