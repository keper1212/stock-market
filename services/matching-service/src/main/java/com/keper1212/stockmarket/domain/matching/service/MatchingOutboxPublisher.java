package com.keper1212.stockmarket.domain.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.common.event.OutboxKafkaMessage;
import com.keper1212.stockmarket.domain.matching.entity.MatchingOutboxEvent;
import com.keper1212.stockmarket.domain.matching.repository.MatchingOutboxEventRepository;
import java.util.List;
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
@ConditionalOnProperty(prefix = "app.matching-outbox.publisher", name = "enabled", havingValue = "true")
public class MatchingOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(MatchingOutboxPublisher.class);

    private final MatchingOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.matching-outbox.publisher.batch-size}")
    private int batchSize;

    @Value("${app.matching-outbox.publisher.send-timeout-seconds}")
    private long sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${app.matching-outbox.publisher.fixed-delay-ms}")
    public void publishPendingEvents() {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> outboxEventRepository.findPendingEventsForPublish(batchSize).forEach(this::publish)
        );
    }

    private void publish(MatchingOutboxEvent event) {
        try {
            String message = objectMapper.writeValueAsString(new OutboxKafkaMessage(
                    event.getEventId(), event.getEventType(), event.getPayload()
            ));
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), message)
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);
            event.markSent();
        } catch (Exception e) {
            String error = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            event.recordPublishFailure(error);
            log.warn("Matching outbox event publish failed. eventId={}, error={}", event.getEventId(), error);
        }
    }
}
