package com.keper1212.stockmarket.domain.realtime.service;

import com.keper1212.stockmarket.domain.marketdata.controller.dto.OrderBookResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StocksResponse;
import com.keper1212.stockmarket.domain.marketdata.service.StockMarketDataService;
import com.keper1212.stockmarket.domain.realtime.dto.TradeExecutedRealtimeMessage;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private static final String STOCKS_TOPIC = "/topic/stocks";
    private static final String ORDERBOOK_TOPIC_FORMAT = "/topic/stocks/%s/orderbook";
    private static final String TRADES_TOPIC_FORMAT = "/topic/stocks/%s/trades";

    private final SimpMessagingTemplate messagingTemplate;
    private final StockMarketDataService stockMarketDataService;
    private final Object pendingMarketSnapshotLock = new Object();
    private final Set<String> pendingMarketSnapshotStockCodes = new HashSet<>();

    public void requestMarketSnapshots(String stockCode) {
        synchronized (pendingMarketSnapshotLock) {
            pendingMarketSnapshotStockCodes.add(stockCode);
        }
    }

    @Scheduled(fixedRateString = "${app.realtime.snapshot.flush-interval-ms:100}")
    public void flushPendingMarketSnapshots() {
        Set<String> stockCodes;
        synchronized (pendingMarketSnapshotLock) {
            if (pendingMarketSnapshotStockCodes.isEmpty()) {
                return;
            }
            stockCodes = new HashSet<>(pendingMarketSnapshotStockCodes);
            pendingMarketSnapshotStockCodes.clear();
        }

        publishOrderBookSnapshots(stockCodes);
        publishStocksSnapshot(stockCodes);
    }

    public void publishMarketSnapshots(String stockCode) {
        publishOrderBookSnapshots(Set.of(stockCode));
        publishStocksSnapshot(Set.of(stockCode));
    }

    public void publishOrderBookSnapshot(String stockCode) {
        publishOrderBookSnapshots(Set.of(stockCode));
    }

    public void publishTradeExecuted(TradeExecutedRealtimeMessage message) {
        messagingTemplate.convertAndSend(TRADES_TOPIC_FORMAT.formatted(message.stockCode()), message);
        log.debug("Realtime trade executed published. stockCode={}, tradePrice={}, tradeQuantity={}",
                message.stockCode(), message.tradePrice(), message.tradeQuantity());
    }

    private void publishOrderBookSnapshots(Set<String> stockCodes) {
        for (String stockCode : stockCodes) {
            try {
                OrderBookResponse orderBook = stockMarketDataService.getOrderBook(stockCode);
                messagingTemplate.convertAndSend(ORDERBOOK_TOPIC_FORMAT.formatted(orderBook.stockCode()), orderBook);
            } catch (RuntimeException e) {
                log.warn("Realtime orderbook snapshot publish failed. stockCode={}, error={}", stockCode, e.getMessage());
            }
        }
    }

    private void publishStocksSnapshot(Set<String> stockCodes) {
        try {
            StocksResponse stocks = stockMarketDataService.getStocks();
            messagingTemplate.convertAndSend(STOCKS_TOPIC, stocks);
            log.debug("Realtime market snapshots published. stockCodes={}", stockCodes);
        } catch (RuntimeException e) {
            log.warn("Realtime stocks snapshot publish failed. stockCodes={}, error={}", stockCodes, e.getMessage());
        }
    }
}
