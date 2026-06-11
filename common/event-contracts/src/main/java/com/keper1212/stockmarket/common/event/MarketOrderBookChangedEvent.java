package com.keper1212.stockmarket.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MarketOrderBookChangedEvent(
        UUID marketEventId,
        String stockCode,
        OffsetDateTime changedAt
) {
}
