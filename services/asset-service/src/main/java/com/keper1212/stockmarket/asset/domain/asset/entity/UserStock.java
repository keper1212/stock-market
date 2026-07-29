package com.keper1212.stockmarket.asset.domain.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_stocks")
public class UserStock {

    @Id
    @Column(name = "user_stock_id")
    private Long userStockId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "locked_quantity", nullable = false)
    private long lockedQuantity;

    protected UserStock() {
    }
}
