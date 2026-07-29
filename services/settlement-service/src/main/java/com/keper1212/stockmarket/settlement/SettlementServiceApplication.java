package com.keper1212.stockmarket.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.keper1212.stockmarket")
@EntityScan(basePackages = {
        "com.keper1212.stockmarket.domain.settlement.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.keper1212.stockmarket.domain.settlement.repository"
})
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
