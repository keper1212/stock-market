package com.keper1212.stockmarket.asset.domain.asset.repository;

import com.keper1212.stockmarket.asset.domain.asset.entity.UserStock;
import java.util.List;
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
    List<UserStockAssetView> findHoldingStocksByUserId(@Param("userId") long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_stocks
            SET locked_quantity = locked_quantity + :lockQuantity,
                updated_at = NOW()
            WHERE user_id = :userId
              AND stock_code = :stockCode
              AND (quantity - locked_quantity) >= :lockQuantity
            """, nativeQuery = true)
    int lockQuantityIfAvailable(
            @Param("userId") long userId,
            @Param("stockCode") String stockCode,
            @Param("lockQuantity") long lockQuantity
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_stocks (user_id, stock_code, quantity, locked_quantity, average_cost, created_at, updated_at)
            VALUES (:buyerId, :stockCode, :tradeQuantity, 0, :tradePrice, NOW(), NOW())
            ON CONFLICT (user_id, stock_code)
            DO UPDATE SET
                average_cost = CASE
                    WHEN user_stocks.quantity + EXCLUDED.quantity = 0 THEN 0
                    ELSE ((user_stocks.average_cost * user_stocks.quantity) + (:tradePrice * :tradeQuantity))
                        / (user_stocks.quantity + EXCLUDED.quantity)
                END,
                quantity = user_stocks.quantity + EXCLUDED.quantity,
                updated_at = NOW()
            """, nativeQuery = true)
    int addBuyerStock(
            @Param("buyerId") long buyerId,
            @Param("stockCode") String stockCode,
            @Param("tradeQuantity") long tradeQuantity,
            @Param("tradePrice") long tradePrice
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_stocks
            SET quantity = quantity - :tradeQuantity,
                locked_quantity = locked_quantity - :tradeQuantity,
                updated_at = NOW()
            WHERE user_id = :sellerId
              AND stock_code = :stockCode
              AND quantity >= :tradeQuantity
              AND locked_quantity >= :tradeQuantity
            """, nativeQuery = true)
    int settleSellerStock(
            @Param("sellerId") long sellerId,
            @Param("stockCode") String stockCode,
            @Param("tradeQuantity") long tradeQuantity
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_stocks
            SET locked_quantity = locked_quantity - :unlockQuantity,
                updated_at = NOW()
            WHERE user_id = :userId
              AND stock_code = :stockCode
              AND locked_quantity >= :unlockQuantity
            """, nativeQuery = true)
    int unlockQuantity(
            @Param("userId") long userId,
            @Param("stockCode") String stockCode,
            @Param("unlockQuantity") long unlockQuantity
    );

    interface UserStockAssetView {
        String getStockCode();

        String getStockName();

        long getQuantity();

        double getAverageCost();

        long getBasePrice();
    }
}
