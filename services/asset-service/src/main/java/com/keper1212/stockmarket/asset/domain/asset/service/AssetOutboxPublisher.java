package com.keper1212.stockmarket.asset.domain.asset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keper1212.stockmarket.asset.domain.asset.entity.AssetOutboxEvent;
import com.keper1212.stockmarket.asset.domain.asset.repository.AssetOutboxEventRepository;
import com.keper1212.stockmarket.common.event.OutboxKafkaMessage;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
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
public class AssetOutboxPublisher {

    private final AssetOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.outbox.publisher.batch-size}")
    private int batchSize;

    @Value("${app.outbox.publisher.send-timeout-seconds}")
    private long sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${app.outbox.publisher.fixed-delay-ms}")
    public void publishPendingEvents() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<AssetOutboxEvent> events = outboxEventRepository.findPendingEventsForPublish(batchSize);
            for (AssetOutboxEvent event : events) {
                publish(event);
            }
        });
    }

    private void publish(AssetOutboxEvent event) {
        try {
            String message = objectMapper.writeValueAsString(new OutboxKafkaMessage(
                    event.getEventId(),
                    event.getEventType(),
                    event.getPayload()
            ));
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), message)
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);
            event.markSent();
        } catch (Exception e) {
            event.recordPublishFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
