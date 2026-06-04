package com.keper1212.stockmarket.domain.marketdata.service;

import com.keper1212.stockmarket.domain.marketdata.controller.dto.StockSummaryResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StocksResponse;
import com.keper1212.stockmarket.domain.marketdata.repository.StockMarketDataRepository;
import com.keper1212.stockmarket.domain.marketdata.repository.StockMarketDataRepository.StockDashboardRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMarketDataService {

    private static final String CHANGE_RISE = "RISE";
    private static final String CHANGE_FALL = "FALL";
    private static final String CHANGE_EVEN = "EVEN";

    private final StockMarketDataRepository stockMarketDataRepository;

    @Transactional(readOnly = true)
    public StocksResponse getStocks() {
        List<StockSummaryResponse> stocks = stockMarketDataRepository.findDashboardStocks().stream()
                .map(this::toResponse)
                .toList();
        return new StocksResponse(stocks);
    }

    private StockSummaryResponse toResponse(StockDashboardRow row) {
        long changePrice = row.currentPrice() - row.basePrice();
        return new StockSummaryResponse(
                row.stockCode(),
                row.stockName(),
                row.currentPrice(),
                changeDirection(changePrice),
                Math.abs(changePrice),
                changeRate(changePrice, row.basePrice()),
                row.tradeVolume(),
                row.tradeAmount()
        );
    }

    private String changeDirection(long changePrice) {
        if (changePrice > 0) {
            return CHANGE_RISE;
        }
        if (changePrice < 0) {
            return CHANGE_FALL;
        }
        return CHANGE_EVEN;
    }

    private double changeRate(long changePrice, long basePrice) {
        if (basePrice <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(Math.abs(changePrice))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(basePrice), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
