package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.entity.Order;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUser_UserIdAndClientOrderId(Long userId, String clientOrderId);

    Optional<Order> findByOrderIdAndUser_UserId(UUID orderId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
            SET status = 'CANCEL_REQUESTED',
                cancel_client_id = :clientCancelId,
                cancel_requested_at = :cancelRequestedAt,
                updated_at = NOW()
            WHERE order_id = :orderId
              AND user_id = :userId
              AND remaining_quantity > 0
              AND status IN ('ACCEPTED', 'PARTIALLY_FILLED')
              AND (cancel_client_id IS NULL OR cancel_client_id = :clientCancelId)
            """, nativeQuery = true)
    int requestCancelIfCancelable(
            @Param("orderId") UUID orderId,
            @Param("userId") Long userId,
            @Param("clientCancelId") String clientCancelId,
            @Param("cancelRequestedAt") OffsetDateTime cancelRequestedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
            SET remaining_quantity = :remainingQuantity,
                status = :status,
                updated_at = NOW()
            WHERE order_id = :orderId
            """, nativeQuery = true)
    int updateExecutionState(
            @Param("orderId") UUID orderId,
            @Param("remainingQuantity") long remainingQuantity,
            @Param("status") String status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
            SET remaining_quantity = 0,
                status = 'CANCELED',
                updated_at = NOW()
            WHERE order_id = :orderId
              AND user_id = :userId
              AND status = 'CANCEL_REQUESTED'
              AND remaining_quantity >= :canceledQuantity
            """, nativeQuery = true)
    int completeCancel(
            @Param("orderId") UUID orderId,
            @Param("userId") long userId,
            @Param("canceledQuantity") long canceledQuantity
    );
}
