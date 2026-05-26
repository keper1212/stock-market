package com.keper1212.stockmarket.domain.user.service;

import com.keper1212.stockmarket.domain.account.entity.Account;
import com.keper1212.stockmarket.domain.user.controller.dto.SignupRequest;
import com.keper1212.stockmarket.domain.user.controller.dto.SignupResponse;
import com.keper1212.stockmarket.domain.user.entity.User;
import com.keper1212.stockmarket.domain.user.repository.UserRepository;
import com.keper1212.stockmarket.global.error.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SignupService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final String DEFAULT_NAME = "user";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        String name = resolveName(request.name(), email);

        User user = User.create(email, encodedPassword, name);
        Account account = Account.createInitial(user);
        user.setAccount(account);

        User savedUser = userRepository.save(user);
        return SignupResponse.created(savedUser.getUserId());
    }

    private String resolveName(String requestName, String email) {
        if (StringUtils.hasText(requestName)) {
            return trimToMaxLength(requestName.trim(), MAX_NAME_LENGTH);
        }

        int atIndex = email.indexOf('@');
        String fallback = atIndex > 0 ? email.substring(0, atIndex) : DEFAULT_NAME;
        if (!StringUtils.hasText(fallback)) {
            fallback = DEFAULT_NAME;
        }
        return trimToMaxLength(fallback, MAX_NAME_LENGTH);
    }

    private String trimToMaxLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
