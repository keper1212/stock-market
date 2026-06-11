package com.keper1212.stockmarket.domain.marketdata.controller.dto;

import java.util.List;

public record StockChartResponse(
        String stockCode,
        List<StockChartPointResponse> points
) {
}
