package com.keper1212.stockmarket.domain.userservice.portfolio.dto;

public record HoldingStockResponse(
        String stockCode,
        String stockName,
        long quantity,
        double averageCost,
        long purchaseAmount,
        long currentPrice,
        long evaluationAmount,
        long profitOrLoss,
        double returnRate
) {
}
