package com.keper1212.stockmarket.domain.matching.repository;

import com.keper1212.stockmarket.domain.matching.entity.MatchingOutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchingOutboxEventRepository extends JpaRepository<MatchingOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM matching_outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<MatchingOutboxEvent> findPendingEventsForPublish(@Param("batchSize") int batchSize);
}
