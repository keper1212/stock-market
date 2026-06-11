package com.keper1212.stockmarket.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.keper1212.stockmarket")
@EntityScan(basePackages = {
        "com.keper1212.stockmarket.domain.order.entity",
        "com.keper1212.stockmarket.domain.userservice.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.keper1212.stockmarket.domain.order.repository",
        "com.keper1212.stockmarket.domain.userservice.repository"
})
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
