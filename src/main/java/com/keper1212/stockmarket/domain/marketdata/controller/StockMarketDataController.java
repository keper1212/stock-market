package com.keper1212.stockmarket.domain.marketdata.controller;

import com.keper1212.stockmarket.domain.marketdata.controller.dto.StocksResponse;
import com.keper1212.stockmarket.domain.marketdata.service.StockMarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockMarketDataController {

    private final StockMarketDataService stockMarketDataService;

    @GetMapping
    public ResponseEntity<StocksResponse> getStocks() {
        return ResponseEntity.ok(stockMarketDataService.getStocks());
    }
}
