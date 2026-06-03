package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.entity.Trade;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    boolean existsByTradeEventId(UUID tradeEventId);

    boolean existsByBuyOrderIdAndSellOrderIdAndTradePriceAndTradeQuantity(
            UUID buyOrderId,
            UUID sellOrderId,
            long tradePrice,
            long tradeQuantity
    );
}
