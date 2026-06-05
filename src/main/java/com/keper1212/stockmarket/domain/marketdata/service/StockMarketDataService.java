package com.keper1212.stockmarket.domain.marketdata.service;

import com.keper1212.stockmarket.domain.marketdata.controller.dto.OrderBookLevelResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.OrderBookResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StockChartPointResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StockChartResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StockSummaryResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StocksResponse;
import com.keper1212.stockmarket.domain.marketdata.repository.StockMarketDataRepository;
import com.keper1212.stockmarket.domain.marketdata.repository.StockMarketDataRepository.StockDashboardRow;
import com.keper1212.stockmarket.domain.marketdata.repository.StockMarketDataRepository.StockChartRow;
import com.keper1212.stockmarket.global.error.OrderException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMarketDataService {

    private static final String CHANGE_RISE = "RISE";
    private static final String CHANGE_FALL = "FALL";
    private static final String CHANGE_EVEN = "EVEN";
    private static final String BID = "BID";
    private static final String ASK = "ASK";
    private static final String BID_KEY_PREFIX = "orderbook:bid:";
    private static final String ASK_KEY_PREFIX = "orderbook:ask:";
    private static final String VOLUME_KEY_PREFIX = "orderbook:volume:";
    private static final int DEFAULT_ORDERBOOK_DEPTH = 10;
    private static final int DEFAULT_CHART_LIMIT = 300;

    private final StockMarketDataRepository stockMarketDataRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(readOnly = true)
    public StocksResponse getStocks() {
        List<StockSummaryResponse> stocks = stockMarketDataRepository.findDashboardStocks().stream()
                .map(this::toResponse)
                .toList();
        return new StocksResponse(stocks);
    }

    @Transactional(readOnly = true)
    public OrderBookResponse getOrderBook(String stockCode) {
        String normalizedStockCode = normalizeStockCode(stockCode);
        long currentPrice = stockMarketDataRepository.findCurrentPriceByStockCode(normalizedStockCode)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, "거래 가능한 종목이 존재하지 않습니다."));

        return new OrderBookResponse(
                normalizedStockCode,
                currentPrice,
                findAskOrders(normalizedStockCode),
                findBidOrders(normalizedStockCode)
        );
    }


    @Transactional(readOnly = true)
    public StockChartResponse getChart(String stockCode) {
        String normalizedStockCode = normalizeStockCode(stockCode);
        if (stockMarketDataRepository.findCurrentPriceByStockCode(normalizedStockCode).isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND, "거래 가능한 종목이 존재하지 않습니다.");
        }

        List<StockChartPointResponse> points = stockMarketDataRepository.findChartPointsByStockCode(normalizedStockCode, DEFAULT_CHART_LIMIT).stream()
                .map(this::toChartPoint)
                .toList();
        return new StockChartResponse(normalizedStockCode, points);
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


    private StockChartPointResponse toChartPoint(StockChartRow row) {
        return new StockChartPointResponse(row.time(), row.price(), row.quantity());
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

    private List<OrderBookLevelResponse> findAskOrders(String stockCode) {
        Set<String> prices = stringRedisTemplate.opsForZSet()
                .range(ASK_KEY_PREFIX + stockCode, 0, DEFAULT_ORDERBOOK_DEPTH - 1);
        return toOrderBookLevels(stockCode, ASK, prices);
    }

    private List<OrderBookLevelResponse> findBidOrders(String stockCode) {
        Set<String> prices = stringRedisTemplate.opsForZSet()
                .reverseRange(BID_KEY_PREFIX + stockCode, 0, DEFAULT_ORDERBOOK_DEPTH - 1);
        return toOrderBookLevels(stockCode, BID, prices);
    }

    private List<OrderBookLevelResponse> toOrderBookLevels(String stockCode, String side, Set<String> prices) {
        if (prices == null || prices.isEmpty()) {
            return Collections.emptyList();
        }

        String volumeKey = VOLUME_KEY_PREFIX + stockCode;
        return prices.stream()
                .map(price -> toOrderBookLevel(volumeKey, side, price))
                .filter(Objects::nonNull)
                .toList();
    }

    private OrderBookLevelResponse toOrderBookLevel(String volumeKey, String side, String priceText) {
        long price = parsePositiveLong(priceText);
        String quantityText = (String) stringRedisTemplate.opsForHash().get(volumeKey, side + ":" + priceText);
        if (quantityText == null) {
            return null;
        }

        long quantity = parsePositiveLong(quantityText);
        if (quantity <= 0) {
            return null;
        }

        return new OrderBookLevelResponse(price, quantity);
    }

    private long parsePositiveLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Redis 호가창 데이터 형식이 올바르지 않습니다.", e);
        }
    }

    private String normalizeStockCode(String stockCode) {
        return stockCode.trim().toUpperCase(Locale.ROOT);
    }
}
