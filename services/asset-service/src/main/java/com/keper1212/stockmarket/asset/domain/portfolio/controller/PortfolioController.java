package com.keper1212.stockmarket.asset.domain.portfolio.controller;

import com.keper1212.stockmarket.asset.domain.portfolio.dto.UserAssetsResponse;
import com.keper1212.stockmarket.asset.domain.portfolio.service.PortfolioService;
import com.keper1212.stockmarket.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/me/assets")
    public ResponseEntity<UserAssetsResponse> getMyAssets(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(portfolioService.getMyAssets(currentUser.userId()));
    }
}
