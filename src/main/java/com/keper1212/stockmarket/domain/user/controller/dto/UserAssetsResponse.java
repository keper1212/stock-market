package com.keper1212.stockmarket.domain.user.controller.dto;

import java.util.List;

public record UserAssetsResponse(
        long totalAsset,
        long cashBalance,
        long totalPurchaseAmount,
        long totalEvaluationAmount,
        long totalProfitOrLoss,
        double totalReturnRate,
        List<HoldingStockResponse> holdingStocks
) {
}
