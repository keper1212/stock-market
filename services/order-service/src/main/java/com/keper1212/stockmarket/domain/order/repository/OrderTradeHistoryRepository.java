package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.entity.OrderTradeHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTradeHistoryRepository extends JpaRepository<OrderTradeHistory, Long> {

    boolean existsByTradeEventIdAndOrderId(UUID tradeEventId, UUID orderId);
}
