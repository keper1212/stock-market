package com.keper1212.stockmarket.asset.domain.asset.repository;

import com.keper1212.stockmarket.asset.domain.asset.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query(value = """
            SELECT
                cash_balance AS cashBalance,
                locked_cash AS lockedCash
            FROM accounts
            WHERE user_id = :userId
            """, nativeQuery = true)
    AccountBalanceView findBalanceByUserId(@Param("userId") long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET locked_cash = locked_cash + :lockAmount,
                updated_at = NOW()
            WHERE user_id = :userId
              AND (cash_balance - locked_cash) >= :lockAmount
            """, nativeQuery = true)
    int lockCashIfAvailable(@Param("userId") long userId, @Param("lockAmount") long lockAmount);

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
    int unlockCash(@Param("userId") long userId, @Param("unlockAmount") long unlockAmount);

    interface AccountBalanceView {
        long getCashBalance();

        long getLockedCash();
    }
}
