package com.keper1212.stockmarket.domain.realtime.service;

import com.keper1212.stockmarket.domain.marketdata.controller.dto.OrderBookResponse;
import com.keper1212.stockmarket.domain.marketdata.controller.dto.StocksResponse;
import com.keper1212.stockmarket.domain.marketdata.service.StockMarketDataService;
import com.keper1212.stockmarket.domain.realtime.dto.TradeExecutedRealtimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    public void publishMarketSnapshots(String stockCode) {
        try {
            OrderBookResponse orderBook = stockMarketDataService.getOrderBook(stockCode);
            StocksResponse stocks = stockMarketDataService.getStocks();

            messagingTemplate.convertAndSend(ORDERBOOK_TOPIC_FORMAT.formatted(orderBook.stockCode()), orderBook);
            messagingTemplate.convertAndSend(STOCKS_TOPIC, stocks);
            log.info("Realtime market snapshots published. stockCode={}", orderBook.stockCode());
        } catch (RuntimeException e) {
            log.warn("Realtime market snapshot publish failed. stockCode={}, error={}", stockCode, e.getMessage());
        }
    }

    public void publishTradeExecuted(TradeExecutedRealtimeMessage message) {
        messagingTemplate.convertAndSend(TRADES_TOPIC_FORMAT.formatted(message.stockCode()), message);
        log.info("Realtime trade executed published. stockCode={}, tradePrice={}, tradeQuantity={}",
                message.stockCode(), message.tradePrice(), message.tradeQuantity());
    }
}
