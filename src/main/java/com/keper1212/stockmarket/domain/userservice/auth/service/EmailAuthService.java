package com.keper1212.stockmarket.domain.userservice.auth.service;

import com.keper1212.stockmarket.domain.userservice.auth.dto.EmailCodeRequest;
import com.keper1212.stockmarket.domain.userservice.auth.dto.EmailCodeResponse;
import com.keper1212.stockmarket.domain.userservice.auth.dto.EmailCodeVerifyRequest;
import com.keper1212.stockmarket.domain.userservice.auth.dto.EmailCodeVerifyResponse;
import com.keper1212.stockmarket.domain.userservice.repository.UserRepository;
import com.keper1212.stockmarket.global.error.DuplicateEmailException;
import com.keper1212.stockmarket.global.error.EmailRequestException;
import com.keper1212.stockmarket.global.error.EmailVerificationException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private static final String EMAIL_CODE_KEY_PREFIX = "auth:email:";
    private static final String EMAIL_VERIFIED_KEY_PREFIX = "auth:email:verified:";
    private static final String MAIL_SUBJECT = "[StockMarket] 이메일 인증번호 안내";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            """
            local cached = redis.call('GET', KEYS[1])
            if not cached then
                return -1
            end
            if cached ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;

    @Value("${app.auth.email.from}")
    private String mailFrom;

    @Value("${app.auth.email.code-ttl-seconds}")
    private long codeTtlSeconds;

    @Value("${app.auth.email.verified-ttl-seconds}")
    private long verifiedTtlSeconds;

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.password}")
    private String mailPassword;

    public EmailCodeResponse requestEmailCode(EmailCodeRequest request) {
        validateMailConfiguration();

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }

        String authCode = generateSixDigitCode();
        String redisKey = EMAIL_CODE_KEY_PREFIX + email;

        stringRedisTemplate.opsForValue().set(redisKey, authCode, Duration.ofSeconds(codeTtlSeconds));
        sendEmailAsync(email, authCode, redisKey);

        return EmailCodeResponse.sent(codeTtlSeconds);
    }

    public EmailCodeVerifyResponse verifyEmailCode(EmailCodeVerifyRequest request) {
        String email = normalizeEmail(request.email());
        String redisKey = EMAIL_CODE_KEY_PREFIX + email;
        String submittedCode = request.code().trim();

        Long result = stringRedisTemplate.execute(
                VERIFY_AND_DELETE_SCRIPT,
                Collections.singletonList(redisKey),
                submittedCode
        );

        if (result == null) {
            throw new EmailVerificationException(HttpStatus.INTERNAL_SERVER_ERROR, "인증 검증 처리 중 오류가 발생했습니다.");
        }
        if (result == -1L) {
            throw new EmailVerificationException(HttpStatus.BAD_REQUEST, "인증번호가 만료되었거나 요청 기록이 없습니다.");
        }
        if (result == 0L) {
            throw new EmailVerificationException(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다.");
        }

        String verifiedKey = EMAIL_VERIFIED_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(verifiedKey, "true", Duration.ofSeconds(verifiedTtlSeconds));

        return EmailCodeVerifyResponse.verified();
    }

    @Async("mailTaskExecutor")
    public void sendEmailAsync(String email, String authCode, String redisKey) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (StringUtils.hasText(mailFrom)) {
                message.setFrom(mailFrom);
            }
            message.setTo(email);
            message.setSubject(MAIL_SUBJECT);
            message.setText("인증번호는 [" + authCode + "] 입니다. 5분 안에 입력해 주세요.");
            javaMailSender.send(message);
        } catch (Exception e) {
            log.error("이메일 발송 실패 - email={}", email, e);
            // 발송 실패 시 오래된 인증코드가 검증에 사용되지 않도록 즉시 삭제
            stringRedisTemplate.delete(redisKey);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String generateSixDigitCode() {
        int number = SECURE_RANDOM.nextInt(900_000) + 100_000;
        return Integer.toString(number);
    }

    private void validateMailConfiguration() {
        if (!StringUtils.hasText(mailHost)
                || !StringUtils.hasText(mailUsername)
                || !StringUtils.hasText(mailPassword)) {
            throw new EmailRequestException("이메일 발송 설정이 완료되지 않았습니다. MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD를 확인해 주세요.");
        }
    }
}
