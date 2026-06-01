package com.keper1212.stockmarket.domain.user.controller;

import com.keper1212.stockmarket.domain.user.controller.dto.UserAssetsResponse;
import com.keper1212.stockmarket.domain.user.service.UserAssetService;
import com.keper1212.stockmarket.global.error.AuthException;
import com.keper1212.stockmarket.global.util.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAssetController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAssetService userAssetService;

    @GetMapping("/me/assets")
    public ResponseEntity<UserAssetsResponse> getMyAssets(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = extractUserId(authorization);
        UserAssetsResponse response = userAssetService.getMyAssets(userId);
        return ResponseEntity.ok(response);
    }

    private Long extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Access Token이 필요합니다.");
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Access Token이 필요합니다.");
        }

        try {
            return jwtTokenProvider.getUserIdFromToken(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Access Token이 유효하지 않습니다.");
        }
    }
}
