package com.keper1212.stockmarket.domain.order.repository;

import com.keper1212.stockmarket.domain.order.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUser_UserIdAndClientOrderId(Long userId, String clientOrderId);
}
