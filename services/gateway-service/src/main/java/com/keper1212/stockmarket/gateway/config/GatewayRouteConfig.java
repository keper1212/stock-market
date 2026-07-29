package com.keper1212.stockmarket.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${services.auth.uri:http://localhost:8087}") String authServiceUri,
            @Value("${services.asset.uri:http://localhost:8088}") String assetServiceUri,
            @Value("${services.order.uri:http://localhost:8082}") String orderServiceUri,
            @Value("${services.marketdata.uri:http://localhost:8085}") String marketDataServiceUri,
            @Value("${services.realtime.ws-uri:ws://localhost:8086}") String realtimeServiceWsUri
    ) {
        return builder.routes()
                .route("auth-service", route -> route
                        .path("/api/v1/auth/**")
                        .uri(authServiceUri))
                .route("asset-service", route -> route
                        .path("/api/v1/users/**")
                        .uri(assetServiceUri))
                .route("order-service", route -> route
                        .path("/api/v1/orders/**")
                        .uri(orderServiceUri))
                .route("marketdata-service", route -> route
                        .path("/api/v1/stocks/**")
                        .uri(marketDataServiceUri))
                .route("realtime-service-websocket", route -> route
                        .path("/ws", "/ws/**")
                        .uri(realtimeServiceWsUri))
                .build();
    }
}
