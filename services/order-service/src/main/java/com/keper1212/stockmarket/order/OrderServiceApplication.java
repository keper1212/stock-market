package com.keper1212.stockmarket.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EntityScan(basePackages = "com.keper1212.stockmarket.domain")
@EnableJpaRepositories(basePackages = "com.keper1212.stockmarket.domain")
@SpringBootApplication(scanBasePackages = "com.keper1212.stockmarket")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
