package com.keper1212.stockmarket.domain.userservice.repository;

import com.keper1212.stockmarket.domain.userservice.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser_UserId(Long userId);

    boolean existsByUser_UserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET locked_cash = locked_cash + :lockAmount,
                updated_at = NOW()
            WHERE user_id = :userId
              AND (cash_balance - locked_cash) >= :lockAmount
            """, nativeQuery = true)
    int lockCashByUserIdIfAvailable(@Param("userId") Long userId, @Param("lockAmount") long lockAmount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET cash_balance = cash_balance - :tradeAmount,
                locked_cash = locked_cash - :lockedAmount,
                updated_at = NOW()
            WHERE user_id = :buyerId
              AND locked_cash >= :lockedAmount
              AND cash_balance >= :tradeAmount
            """, nativeQuery = true)
    int settleBuyerCash(
            @Param("buyerId") long buyerId,
            @Param("lockedAmount") long lockedAmount,
            @Param("tradeAmount") long tradeAmount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET cash_balance = cash_balance + :tradeAmount,
                updated_at = NOW()
            WHERE user_id = :sellerId
            """, nativeQuery = true)
    int settleSellerCash(@Param("sellerId") long sellerId, @Param("tradeAmount") long tradeAmount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET locked_cash = locked_cash - :unlockAmount,
                updated_at = NOW()
            WHERE user_id = :userId
              AND locked_cash >= :unlockAmount
            """, nativeQuery = true)
    int unlockCashByUserId(@Param("userId") long userId, @Param("unlockAmount") long unlockAmount);
}
