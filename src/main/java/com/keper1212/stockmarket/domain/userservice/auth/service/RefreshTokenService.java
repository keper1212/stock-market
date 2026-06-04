package com.keper1212.stockmarket.domain.userservice.auth.service;

import com.keper1212.stockmarket.domain.userservice.auth.dto.RefreshTokenResponse;
import com.keper1212.stockmarket.domain.userservice.repository.UserRepository;
import com.keper1212.stockmarket.global.error.AuthException;
import com.keper1212.stockmarket.global.util.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 필요합니다.");
        }

        Long userId = extractUserId(refreshToken.trim());
        String redisKey = REFRESH_TOKEN_KEY_PREFIX + userId;
        String savedRefreshToken = stringRedisTemplate.opsForValue().get(redisKey);

        if (!StringUtils.hasText(savedRefreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었거나 존재하지 않습니다.");
        }
        if (!savedRefreshToken.equals(refreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다.");
        }
        if (!userRepository.existsById(userId)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "사용자 정보가 존재하지 않습니다.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        return RefreshTokenResponse.success(newAccessToken);
    }


    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken.trim());
            stringRedisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Invalid tokens are treated as already logged out.
        }
    }

    private Long extractUserId(String refreshToken) {
        try {
            return jwtTokenProvider.getUserIdFromToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다.");
        }
    }
}
