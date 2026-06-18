package com.keper1212.stockmarket.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityCommonBeans {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenVerifier jwtTokenVerifier) {
        return new JwtAuthenticationFilter(jwtTokenVerifier);
    }

    @Bean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }
}
