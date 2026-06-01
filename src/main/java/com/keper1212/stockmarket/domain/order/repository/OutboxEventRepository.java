package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.entity.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
