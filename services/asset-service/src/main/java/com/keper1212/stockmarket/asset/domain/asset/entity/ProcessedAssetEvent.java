package com.keper1212.stockmarket.asset.domain.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "asset_processed_events")
public class ProcessedAssetEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedAssetEvent() {
    }

    public ProcessedAssetEvent(UUID eventId) {
        this.eventId = eventId;
    }

    @PrePersist
    void prePersist() {
        this.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
