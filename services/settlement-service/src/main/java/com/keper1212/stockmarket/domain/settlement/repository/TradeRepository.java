package com.keper1212.stockmarket.domain.settlement.repository;

import com.keper1212.stockmarket.domain.settlement.entity.Trade;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    boolean existsByTradeEventId(UUID tradeEventId);
}
