package com.keper1212.stockmarket.domain.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.domain.order.entity.OutboxEvent;
import com.keper1212.stockmarket.domain.order.repository.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox.publisher", name = "enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.outbox.publisher.batch-size}")
    private int batchSize;

    @Value("${app.outbox.publisher.send-timeout-seconds}")
    private long sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${app.outbox.publisher.fixed-delay-ms}")
    public void publishPendingEvents() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> publishPendingEventsInTransaction());
    }

    private void publishPendingEventsInTransaction() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForPublish(batchSize);
        for (OutboxEvent event : pendingEvents) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            String message = objectMapper.writeValueAsString(OutboxKafkaMessage.from(event));
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), message)
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);
            event.markSent(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception e) {
            String errorMessage = resolveErrorMessage(e);
            event.recordPublishFailure(errorMessage);
            log.warn("Outbox event publish failed. eventId={}, error={}", event.getEventId(), errorMessage);
        }
    }

    private String resolveErrorMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof JsonProcessingException) {
            return "JSON payload serialization failed: " + cause.getMessage();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private record OutboxKafkaMessage(
            UUID eventId,
            String eventType,
            Object payload
    ) {

        private static OutboxKafkaMessage from(OutboxEvent event) {
            return new OutboxKafkaMessage(
                    event.getEventId(),
                    event.getEventType(),
                    event.getPayload()
            );
        }
    }
}
