package com.keper1212.stockmarket.domain.userservice.auth.service;

import com.keper1212.stockmarket.domain.userservice.auth.dto.LoginRequest;
import com.keper1212.stockmarket.domain.userservice.auth.dto.LoginResponse;
import com.keper1212.stockmarket.domain.userservice.entity.User;
import com.keper1212.stockmarket.domain.userservice.repository.UserRepository;
import com.keper1212.stockmarket.global.error.AuthException;
import com.keper1212.stockmarket.global.util.JwtTokenProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    public LoginResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
        long refreshTokenTtlSeconds = jwtTokenProvider.getRefreshTokenValiditySeconds();

        String redisKey = REFRESH_TOKEN_KEY_PREFIX + user.getUserId();
        stringRedisTemplate.opsForValue().set(redisKey, refreshToken, Duration.ofSeconds(refreshTokenTtlSeconds));

        LoginResponse response = LoginResponse.success(user.getUserId(), accessToken);
        return new LoginResult(response, refreshToken, refreshTokenTtlSeconds);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    public record LoginResult(
            LoginResponse response,
            String refreshToken,
            long refreshTokenTtlSeconds
    ) {
    }
}
