package com.keper1212.stockmarket.domain.userservice.repository;

import com.keper1212.stockmarket.domain.userservice.entity.UserStock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query(value = """
            SELECT us.quantity
            FROM user_stocks us
            WHERE us.user_id = :userId
              AND us.stock_code = :stockCode
            """, nativeQuery = true)
    Optional<Long> findQuantityByUserIdAndStockCode(@Param("userId") Long userId, @Param("stockCode") String stockCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_stocks
            SET locked_quantity = locked_quantity + :lockQuantity,
                updated_at = NOW()
            WHERE user_id = :userId
              AND stock_code = :stockCode
              AND (quantity - locked_quantity) >= :lockQuantity
            """, nativeQuery = true)
    int lockQuantityByUserIdAndStockCodeIfAvailable(
            @Param("userId") Long userId,
            @Param("stockCode") String stockCode,
            @Param("lockQuantity") long lockQuantity
    );

    interface UserStockAssetView {
        String getStockCode();

        String getStockName();

        long getQuantity();

        double getAverageCost();

        long getBasePrice();
    }
}
