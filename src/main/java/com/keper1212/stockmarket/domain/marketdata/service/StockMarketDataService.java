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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final String MARKET_STOCKS_KEY = "market:stocks";
    private static final String MARKET_STOCK_KEY_PREFIX = "market:stock:";
    private static final int DEFAULT_ORDERBOOK_DEPTH = 10;
    private static final int DEFAULT_CHART_LIMIT = 300;

    private final StockMarketDataRepository stockMarketDataRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(readOnly = true)
    public StocksResponse getStocks() {
        List<StockSummaryResponse> stocks = findMarketSnapshotsFromRedis();
        if (stocks.isEmpty()) {
            stocks = initializeMarketSnapshotsFromDatabase();
        }
        return new StocksResponse(stocks);
    }

    @Transactional(readOnly = true)
    public OrderBookResponse getOrderBook(String stockCode) {
        String normalizedStockCode = normalizeStockCode(stockCode);
        long currentPrice = findCurrentPriceFromRedis(normalizedStockCode)
                .or(() -> stockMarketDataRepository.findCurrentPriceByStockCode(normalizedStockCode))
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, "거래 가능한 종목이 존재하지 않습니다."));

        return new OrderBookResponse(
                normalizedStockCode,
                currentPrice,
                findAskOrders(normalizedStockCode),
                findBidOrders(normalizedStockCode)
        );
    }

    public void recordTradeSnapshot(String stockCode, long tradePrice, long tradeQuantity) {
        String normalizedStockCode = normalizeStockCode(stockCode);
        String marketStockKey = marketStockKey(normalizedStockCode);
        Map<Object, Object> snapshot = stringRedisTemplate.opsForHash().entries(marketStockKey);
        if (snapshot.isEmpty()) {
            initializeMarketSnapshotFromDatabase(normalizedStockCode);
            return;
        }

        long basePrice = parsePositiveLong(requiredSnapshotValue(snapshot, "basePrice"));
        long changePrice = tradePrice - basePrice;
        long tradeAmount = Math.multiplyExact(tradePrice, tradeQuantity);
        Long updatedTradeVolume = stringRedisTemplate.opsForHash().increment(marketStockKey, "tradeVolume", tradeQuantity);
        Long updatedTradeAmount = stringRedisTemplate.opsForHash().increment(marketStockKey, "tradeAmount", tradeAmount);

        stringRedisTemplate.opsForSet().add(MARKET_STOCKS_KEY, normalizedStockCode);
        stringRedisTemplate.opsForHash().putAll(marketStockKey, Map.of(
                "currentPrice", Long.toString(tradePrice),
                "changeDirection", changeDirection(changePrice),
                "changePrice", Long.toString(Math.abs(changePrice)),
                "changeRate", Double.toString(changeRate(changePrice, basePrice)),
                "tradeVolume", Long.toString(updatedTradeVolume == null ? tradeQuantity : updatedTradeVolume),
                "tradeAmount", Long.toString(updatedTradeAmount == null ? tradeAmount : updatedTradeAmount)
        ));
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

    private List<StockSummaryResponse> findMarketSnapshotsFromRedis() {
        Set<String> stockCodes = stringRedisTemplate.opsForSet().members(MARKET_STOCKS_KEY);
        if (stockCodes == null || stockCodes.isEmpty()) {
            return Collections.emptyList();
        }

        return stockCodes.stream()
                .sorted()
                .map(this::findMarketSnapshotFromRedis)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(StockSummaryResponse::stockCode))
                .toList();
    }

    private Optional<StockSummaryResponse> findMarketSnapshotFromRedis(String stockCode) {
        Map<Object, Object> snapshot = stringRedisTemplate.opsForHash().entries(marketStockKey(stockCode));
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new StockSummaryResponse(
                    requiredSnapshotValue(snapshot, "stockCode"),
                    requiredSnapshotValue(snapshot, "stockName"),
                    parsePositiveLong(requiredSnapshotValue(snapshot, "currentPrice")),
                    requiredSnapshotValue(snapshot, "changeDirection"),
                    parsePositiveLong(requiredSnapshotValue(snapshot, "changePrice")),
                    parseDouble(requiredSnapshotValue(snapshot, "changeRate")),
                    parsePositiveLong(requiredSnapshotValue(snapshot, "tradeVolume")),
                    parsePositiveLong(requiredSnapshotValue(snapshot, "tradeAmount"))
            ));
        } catch (IllegalStateException e) {
            stringRedisTemplate.delete(marketStockKey(stockCode));
            return Optional.empty();
        }
    }

    private List<StockSummaryResponse> initializeMarketSnapshotsFromDatabase() {
        return stockMarketDataRepository.findDashboardStocks().stream()
                .peek(this::writeMarketSnapshot)
                .map(this::toResponse)
                .toList();
    }

    private void initializeMarketSnapshotFromDatabase(String stockCode) {
        stockMarketDataRepository.findDashboardStockByStockCode(stockCode)
                .ifPresent(this::writeMarketSnapshot);
    }

    private void writeMarketSnapshot(StockDashboardRow row) {
        StockSummaryResponse response = toResponse(row);
        stringRedisTemplate.opsForSet().add(MARKET_STOCKS_KEY, row.stockCode());
        stringRedisTemplate.opsForHash().putAll(marketStockKey(row.stockCode()), Map.of(
                "stockCode", response.stockCode(),
                "stockName", response.stockName(),
                "basePrice", Long.toString(row.basePrice()),
                "currentPrice", Long.toString(response.currentPrice()),
                "changeDirection", response.changeDirection(),
                "changePrice", Long.toString(response.changePrice()),
                "changeRate", Double.toString(response.changeRate()),
                "tradeVolume", Long.toString(response.tradeVolume()),
                "tradeAmount", Long.toString(response.tradeAmount())
        ));
    }

    private Optional<Long> findCurrentPriceFromRedis(String stockCode) {
        Object currentPrice = stringRedisTemplate.opsForHash().get(marketStockKey(stockCode), "currentPrice");
        if (currentPrice == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parsePositiveLong(currentPrice.toString()));
        } catch (IllegalStateException e) {
            stringRedisTemplate.delete(marketStockKey(stockCode));
            return Optional.empty();
        }
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

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Redis 시세 데이터 형식이 올바르지 않습니다.", e);
        }
    }

    private String requiredSnapshotValue(Map<Object, Object> snapshot, String fieldName) {
        Object value = snapshot.get(fieldName);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Redis 시세 데이터가 누락되었습니다: " + fieldName);
        }
        return value.toString();
    }

    private String marketStockKey(String stockCode) {
        return MARKET_STOCK_KEY_PREFIX + stockCode;
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
