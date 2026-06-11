package com.keper1212.stockmarket.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.keper1212.stockmarket")
public class RealtimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeServiceApplication.class, args);
    }
}
