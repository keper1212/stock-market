package com.keper1212.stockmarket.asset.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                // Keep event timestamps compatible with all service consumers.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
