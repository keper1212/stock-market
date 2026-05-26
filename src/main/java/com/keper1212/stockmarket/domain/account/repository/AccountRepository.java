package com.keper1212.stockmarket.domain.account.repository;

import com.keper1212.stockmarket.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
