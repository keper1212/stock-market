package com.keper1212.stockmarket.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.keper1212.stockmarket")
@EntityScan(basePackages = "com.keper1212.stockmarket.domain.order.entity")
@EnableJpaRepositories(basePackages = "com.keper1212.stockmarket.domain.order.repository")
public class MatchingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingServiceApplication.class, args);
    }
}
