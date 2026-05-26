package com.keper1212.stockmarket.domain.user.controller;

import com.keper1212.stockmarket.domain.user.controller.dto.EmailCodeRequest;
import com.keper1212.stockmarket.domain.user.controller.dto.EmailCodeResponse;
import com.keper1212.stockmarket.domain.user.controller.dto.EmailCodeVerifyRequest;
import com.keper1212.stockmarket.domain.user.controller.dto.EmailCodeVerifyResponse;
import com.keper1212.stockmarket.domain.user.controller.dto.LoginRequest;
import com.keper1212.stockmarket.domain.user.controller.dto.LoginResponse;
import com.keper1212.stockmarket.domain.user.controller.dto.RefreshTokenResponse;
import com.keper1212.stockmarket.domain.user.controller.dto.SignupRequest;
import com.keper1212.stockmarket.domain.user.controller.dto.SignupResponse;
import com.keper1212.stockmarket.domain.user.service.EmailAuthService;
import com.keper1212.stockmarket.domain.user.service.LoginService;
import com.keper1212.stockmarket.domain.user.service.RefreshTokenService;
import com.keper1212.stockmarket.domain.user.service.SignupService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final EmailAuthService emailAuthService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.auth.jwt.refresh-cookie-name}")
    private String refreshCookieName;

    @Value("${app.auth.jwt.refresh-cookie-secure}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.jwt.refresh-cookie-same-site}")
    private String refreshCookieSameSite;

    @PostMapping("/email/request")
    public ResponseEntity<EmailCodeResponse> requestEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        EmailCodeResponse response = emailAuthService.requestEmailCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<EmailCodeVerifyResponse> verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
        EmailCodeVerifyResponse response = emailAuthService.verifyEmailCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginService.LoginResult loginResult = loginService.login(request);

        ResponseCookie refreshCookie = ResponseCookie.from(refreshCookieName, loginResult.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path("/")
                .maxAge(loginResult.refreshTokenTtlSeconds())
                .sameSite(refreshCookieSameSite)
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(loginResult.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(HttpServletRequest httpServletRequest) {
        String refreshToken = extractRefreshTokenFromCookie(httpServletRequest);
        RefreshTokenResponse response = refreshTokenService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest httpServletRequest) {
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
