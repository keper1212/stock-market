package com.keper1212.stockmarket.asset.domain.portfolio.service;

import com.keper1212.stockmarket.asset.domain.asset.repository.AccountRepository;
import com.keper1212.stockmarket.asset.domain.asset.repository.UserStockRepository;
import com.keper1212.stockmarket.asset.domain.portfolio.dto.HoldingStockResponse;
import com.keper1212.stockmarket.asset.domain.portfolio.dto.UserAssetsResponse;
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
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final String STOCK_INFO_KEY_PREFIX = "stock:info:";
    private static final String STOCK_INFO_CURRENT_PRICE_FIELD = "currentPrice";

    private final AccountRepository accountRepository;
    private final UserStockRepository userStockRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(readOnly = true)
    public UserAssetsResponse getMyAssets(long userId) {
        AccountRepository.AccountBalanceView account = accountRepository.findBalanceByUserId(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계좌 정보가 존재하지 않습니다.");
        }

        List<UserStockRepository.UserStockAssetView> holdings = userStockRepository.findHoldingStocksByUserId(userId);
        Map<String, Long> currentPrices = fetchCurrentPrices(holdings);

        List<HoldingStockResponse> holdingStocks = new ArrayList<>(holdings.size());
        long totalPurchaseAmount = 0L;
        long totalEvaluationAmount = 0L;

        for (UserStockRepository.UserStockAssetView holding : holdings) {
            long quantity = holding.getQuantity();
            long purchaseAmount = toRoundedLong(BigDecimal.valueOf(holding.getAverageCost()).multiply(BigDecimal.valueOf(quantity)));
            long currentPrice = currentPrices.getOrDefault(holding.getStockCode(), holding.getBasePrice());
            long evaluationAmount = quantity * currentPrice;
            long profitOrLoss = evaluationAmount - purchaseAmount;

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
                    calculateReturnRate(profitOrLoss, purchaseAmount)
            ));
        }

        long cashBalance = account.getCashBalance();
        long lockedCash = account.getLockedCash();
        long totalProfitOrLoss = totalEvaluationAmount - totalPurchaseAmount;
        return new UserAssetsResponse(
                cashBalance + totalEvaluationAmount,
                cashBalance,
                cashBalance - lockedCash,
                lockedCash,
                totalPurchaseAmount,
                totalEvaluationAmount,
                totalProfitOrLoss,
                calculateReturnRate(totalProfitOrLoss, totalPurchaseAmount),
                holdingStocks
        );
    }

    private Map<String, Long> fetchCurrentPrices(List<UserStockRepository.UserStockAssetView> holdings) {
        Map<String, Long> result = new HashMap<>();
        if (holdings.isEmpty()) {
            return result;
        }

        List<String> stockCodes = holdings.stream()
                .map(UserStockRepository.UserStockAssetView::getStockCode)
                .toList();
        List<Object> redisResults = stringRedisTemplate.executePipelined((RedisConnection connection) -> {
            StringRedisConnection stringConnection = (StringRedisConnection) connection;
            for (String stockCode : stockCodes) {
                stringConnection.hGet(STOCK_INFO_KEY_PREFIX + stockCode, STOCK_INFO_CURRENT_PRICE_FIELD);
            }
            return null;
        });

        for (int index = 0; index < stockCodes.size(); index++) {
            Object value = redisResults.get(index);
            if (value instanceof String priceText) {
                try {
                    result.put(stockCodes.get(index), Long.parseLong(priceText));
                } catch (NumberFormatException ignored) {
                    // Fall back to the stock base price for malformed cache data.
                }
            }
        }
        return result;
    }

    private double calculateReturnRate(long profitOrLoss, long purchaseAmount) {
        if (purchaseAmount <= 0) {
            return 0.0;
        }
        return roundTwo(BigDecimal.valueOf(profitOrLoss)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(purchaseAmount), 8, RoundingMode.HALF_UP)
                .doubleValue());
    }

    private long toRoundedLong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private double roundTwo(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
