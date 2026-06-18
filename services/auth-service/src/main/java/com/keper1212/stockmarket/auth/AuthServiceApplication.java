package com.keper1212.stockmarket.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EntityScan(basePackages = "com.keper1212.stockmarket.domain.userservice.entity")
@EnableJpaRepositories(basePackages = "com.keper1212.stockmarket.domain.userservice.repository")
@SpringBootApplication(scanBasePackages = "com.keper1212.stockmarket")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
