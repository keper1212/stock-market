package com.keper1212.stockmarket.domain.marketdata.controller.dto;

import java.time.OffsetDateTime;

public record StockChartPointResponse(
        OffsetDateTime time,
        long price,
        long quantity
) {
}
