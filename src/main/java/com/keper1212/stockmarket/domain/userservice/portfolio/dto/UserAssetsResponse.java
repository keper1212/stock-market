package com.keper1212.stockmarket.domain.userservice.portfolio.dto;

import java.util.List;

public record UserAssetsResponse(
        long totalAsset,
        long cashBalance,
        long availableCashBalance,
        long lockedCash,
        long totalPurchaseAmount,
        long totalEvaluationAmount,
        long totalProfitOrLoss,
        double totalReturnRate,
        List<HoldingStockResponse> holdingStocks
) {
}
