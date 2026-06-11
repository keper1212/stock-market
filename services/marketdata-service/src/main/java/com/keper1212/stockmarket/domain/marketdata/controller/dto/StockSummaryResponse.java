package com.keper1212.stockmarket.domain.marketdata.controller.dto;

public record StockSummaryResponse(
        String stockCode,
        String stockName,
        long currentPrice,
        String changeDirection,
        long changePrice,
        double changeRate,
        long tradeVolume,
        long tradeAmount
) {
}
