package com.keper1212.stockmarket.domain.user.service;

import com.keper1212.stockmarket.domain.account.entity.Account;
import com.keper1212.stockmarket.domain.account.repository.AccountRepository;
import com.keper1212.stockmarket.domain.trade.repository.UserStockRepository;
import com.keper1212.stockmarket.domain.user.controller.dto.HoldingStockResponse;
import com.keper1212.stockmarket.domain.user.controller.dto.UserAssetsResponse;
import com.keper1212.stockmarket.global.error.AuthException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAssetService {

    private static final String STOCK_INFO_KEY_PREFIX = "stock:info:";
    private static final String STOCK_INFO_CURRENT_PRICE_FIELD = "currentPrice";

    private final AccountRepository accountRepository;
    private final UserStockRepository userStockRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(readOnly = true)
    public UserAssetsResponse getMyAssets(Long userId) {
        Account account = accountRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "계좌 정보가 존재하지 않습니다."));

        List<UserStockRepository.UserStockAssetView> holdings = userStockRepository.findHoldingStocksByUserId(userId);
        Map<String, Long> currentPriceMap = fetchCurrentPrices(holdings);

        List<HoldingStockResponse> holdingStocks = new ArrayList<>(holdings.size());
        long totalPurchaseAmount = 0L;
        long totalEvaluationAmount = 0L;

        for (UserStockRepository.UserStockAssetView holding : holdings) {
            long quantity = holding.getQuantity();
            long purchaseAmount = toRoundedLong(BigDecimal.valueOf(holding.getAverageCost()).multiply(BigDecimal.valueOf(quantity)));
            long currentPrice = currentPriceMap.getOrDefault(holding.getStockCode(), holding.getBasePrice());
            long evaluationAmount = quantity * currentPrice;
            long profitOrLoss = evaluationAmount - purchaseAmount;
            double returnRate = calculateReturnRate(profitOrLoss, purchaseAmount);

            totalPurchaseAmount += purchaseAmount;
            totalEvaluationAmount += evaluationAmount;

            holdingStocks.add(new HoldingStockResponse(
                    holding.getStockCode(),
                    holding.getStockName(),
                    quantity,
                    roundTwo(holding.getAverageCost()),
                    purchaseAmount,
                    currentPrice,
                    evaluationAmount,
                    profitOrLoss,
                    returnRate
            ));
        }

        long cashBalance = account.getCashBalance();
        long totalAsset = cashBalance + totalEvaluationAmount;
        long totalProfitOrLoss = totalEvaluationAmount - totalPurchaseAmount;
        double totalReturnRate = calculateReturnRate(totalProfitOrLoss, totalPurchaseAmount);

        return new UserAssetsResponse(
                totalAsset,
                cashBalance,
                totalPurchaseAmount,
                totalEvaluationAmount,
                totalProfitOrLoss,
                totalReturnRate,
                holdingStocks
        );
    }

    private Map<String, Long> fetchCurrentPrices(List<UserStockRepository.UserStockAssetView> holdings) {
        Map<String, Long> resultMap = new HashMap<>();
        if (holdings.isEmpty()) {
            return resultMap;
        }

        List<String> stockCodes = holdings.stream()
                .map(UserStockRepository.UserStockAssetView::getStockCode)
                .toList();

        List<Object> redisResults = stringRedisTemplate.executePipelined((RedisConnection connection) -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (String stockCode : stockCodes) {
                stringConn.hGet(STOCK_INFO_KEY_PREFIX + stockCode, STOCK_INFO_CURRENT_PRICE_FIELD);
            }
            return null;
        });

        for (int i = 0; i < stockCodes.size(); i++) {
            Object value = redisResults.get(i);
            if (value instanceof String priceString) {
                try {
                    resultMap.put(stockCodes.get(i), Long.parseLong(priceString));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed cache values and fallback to basePrice.
                }
            }
        }
        return resultMap;
    }

    private double calculateReturnRate(long profitOrLoss, long purchaseAmount) {
        if (purchaseAmount <= 0) {
            return 0.0;
        }
        BigDecimal rate = BigDecimal.valueOf(profitOrLoss)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(purchaseAmount), 8, RoundingMode.HALF_UP);
        return roundTwo(rate.doubleValue());
    }

    private long toRoundedLong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private double roundTwo(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
