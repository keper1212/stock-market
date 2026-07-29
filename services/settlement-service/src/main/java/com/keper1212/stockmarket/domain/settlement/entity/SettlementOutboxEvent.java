package com.keper1212.stockmarket.domain.settlement.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "settlement_outbox_events")
public class SettlementOutboxEvent {

    private static final String PENDING = "PENDING";

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 120)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected SettlementOutboxEvent() {
    }

    private SettlementOutboxEvent(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            JsonNode payload
    ) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = PENDING;
    }

    public static SettlementOutboxEvent pending(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            JsonNode payload
    ) {
        return new SettlementOutboxEvent(eventId, aggregateType, aggregateId, eventType, topic, partitionKey, payload);
    }

    @PrePersist
    void initializeCreatedAt() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markSent() {
        status = "SENT";
        publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        lastError = null;
    }

    public void recordPublishFailure(String errorMessage) {
        status = PENDING;
        retryCount++;
        lastError = errorMessage;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public JsonNode getPayload() { return payload; }
}
