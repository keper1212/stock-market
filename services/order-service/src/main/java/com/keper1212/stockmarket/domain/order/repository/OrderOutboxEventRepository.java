package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.entity.OrderOutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM order_outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderOutboxEvent> findPendingEventsForPublish(@Param("batchSize") int batchSize);
}
