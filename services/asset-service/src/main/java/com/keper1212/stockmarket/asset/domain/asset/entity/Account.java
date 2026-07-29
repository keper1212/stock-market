package com.keper1212.stockmarket.asset.domain.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cash_balance", nullable = false)
    private long cashBalance;

    @Column(name = "locked_cash", nullable = false)
    private long lockedCash;

    protected Account() {
    }
}
