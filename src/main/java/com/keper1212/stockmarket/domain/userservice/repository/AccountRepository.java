package com.keper1212.stockmarket.domain.userservice.repository;

import com.keper1212.stockmarket.domain.userservice.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser_UserId(Long userId);
}
