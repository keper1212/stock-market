package com.keper1212.stockmarket.domain.settlement.repository;

import com.keper1212.stockmarket.domain.settlement.entity.SettlementOutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementOutboxEventRepository extends JpaRepository<SettlementOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM settlement_outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SettlementOutboxEvent> findPendingEventsForPublish(@Param("batchSize") int batchSize);
}
