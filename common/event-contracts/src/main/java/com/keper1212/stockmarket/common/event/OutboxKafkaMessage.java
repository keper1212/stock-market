package com.keper1212.stockmarket.common.event;

import java.util.UUID;

public record OutboxKafkaMessage(
        UUID eventId,
        String eventType,
        Object payload
) {
}
