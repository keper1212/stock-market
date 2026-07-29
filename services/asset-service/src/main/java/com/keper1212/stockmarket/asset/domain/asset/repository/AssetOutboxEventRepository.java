package com.keper1212.stockmarket.asset.domain.asset.repository;

import com.keper1212.stockmarket.asset.domain.asset.entity.AssetOutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetOutboxEventRepository extends JpaRepository<AssetOutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM asset_outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AssetOutboxEvent> findPendingEventsForPublish(@Param("batchSize") int batchSize);
}
