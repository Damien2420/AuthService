package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.entity.User;
import com.login.main.enums.RedisKeyPrefix;
import com.login.main.repository.UserRepository;
import com.login.main.service.EmailService;
import com.login.main.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasswordResetServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private OtpService otpService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_OTP = "123456";
    private static final String TEST_TOKEN = UUID.randomUUID().toString();
    private static final String ENCODED_PASSWORD = "$2a$10$encodedpassword";

    // ===== sendOtp =====

    @Test
    void sendOtp_whenEmailExists_shouldIssueOtpAndSendEmail() {
        User user = buildUser(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(otpService.issueOtp(anyString(), eq(RedisKeyPrefix.OTP_PASSWORD_RESET))).thenReturn(TEST_OTP);

        Result<Void> result = passwordResetService.sendOtp(TEST_EMAIL);

        assertThat(result.isSuccess()).isTrue();
        verify(otpService, times(1)).issueOtp(anyString(), eq(RedisKeyPrefix.OTP_PASSWORD_RESET));
        verify(emailService, times(1)).sendOtpEmail(anyString(), anyString(), eq(RedisKeyPrefix.OTP_PASSWORD_RESET));
    }

    @Test
    void sendOtp_whenEmailNotExists_shouldReturnSuccessWithoutSendingEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Result<Void> result = passwordResetService.sendOtp(TEST_EMAIL);

        assertThat(result.isSuccess()).isTrue();
        verify(otpService, never()).issueOtp(anyString(), any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), any());
    }

    // ===== verifyOtp =====

    @Test
    void verifyOtp_whenOtpValid_shouldReturnResetToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(otpService.verifyAndConsume(anyString(), anyString(), eq(RedisKeyPrefix.OTP_PASSWORD_RESET)))
                .thenReturn(Result.success());

        Result<String> result = passwordResetService.verifyOtp(TEST_EMAIL, TEST_OTP);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull().isNotEmpty();
        verify(valueOperations, times(1)).set(anyString(), anyString(), eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void verifyOtp_whenRedisWriteFails_shouldReturnInternalError() {
        when(otpService.verifyAndConsume(anyString(), anyString(), eq(RedisKeyPrefix.OTP_PASSWORD_RESET)))
                .thenReturn(Result.success());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis 連線失敗"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), eq(TimeUnit.MINUTES));

        Result<String> result = passwordResetService.verifyOtp(TEST_EMAIL, TEST_OTP);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void verifyOtp_whenOtpInvalid_shouldReturnFail() {
        when(otpService.verifyAndConsume(anyString(), anyString(), eq(RedisKeyPrefix.OTP_PASSWORD_RESET)))
                .thenReturn(Result.fail(ErrorCode.OTP_INVALID));

        Result<String> result = passwordResetService.verifyOtp(TEST_EMAIL, TEST_OTP);

        assertThat(result.isFailed()).isTrue();
        verify(redisTemplate, never()).opsForValue();
    }

    // ===== resetPassword =====

    @Test
    void resetPassword_whenTokenValid_shouldResetPasswordAndInvalidateSessions() {
        User user = buildUser(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

        Result<Void> result = passwordResetService.resetPassword(TEST_TOKEN, "newPassword123");

        assertThat(result.isSuccess()).isTrue();
        verify(userRepository, times(1)).save(user);
        verify(redisTemplate, times(1)).delete(anyString());
        verify(valueOperations, times(1)).set(
                contains("invalidation:"),
                anyString(),
                eq(7L),
                eq(TimeUnit.DAYS)
        );
    }

    @Test
    void resetPassword_whenTokenInvalid_shouldReturnFail() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Result<Void> result = passwordResetService.resetPassword("invalid-token", "newPassword123");

        assertThat(result.isFailed()).isTrue();
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void resetPassword_whenUserNotFound_shouldReturnFail() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Result<Void> result = passwordResetService.resetPassword(TEST_TOKEN, "newPassword123");

        assertThat(result.isFailed()).isTrue();
        verify(userRepository, never()).save(any());
    }

    // ===== isTokenInvalidatedByPasswordReset =====

    @Test
    void isTokenInvalidatedByPasswordReset_whenNoRecord_shouldReturnFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        boolean result = passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, new Date());

        assertThat(result).isFalse();
    }

    @Test
    void isTokenInvalidatedByPasswordReset_whenTokenIssuedBeforeReset_shouldReturnTrue() {
        long resetTime = System.currentTimeMillis();
        Date issuedAt = new Date(resetTime - 10000);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(resetTime));

        boolean result = passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt);

        assertThat(result).isTrue();
    }

    @Test
    void isTokenInvalidatedByPasswordReset_whenTokenIssuedAfterReset_shouldReturnFalse() {
        long resetTime = System.currentTimeMillis() - 10000;
        Date issuedAt = new Date(resetTime + 5000);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(String.valueOf(resetTime));

        boolean result = passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt);

        assertThat(result).isFalse();
    }

    @Test
    void isTokenInvalidatedByPasswordReset_whenRedisThrows_shouldThrowRuntimeException() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis error"));

        assertThatThrownBy(() ->
                passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, new Date())
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void isTokenInvalidatedByPasswordReset_whenTimestampFormatInvalid_shouldThrowRuntimeException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("not-a-number");

        assertThatThrownBy(() ->
                passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, new Date())
        ).isInstanceOf(RuntimeException.class);
    }

    // ===== helper =====

    private User buildUser(boolean isVerified) {
        return User.builder()
                .ID(UUID.randomUUID())
                .email(TEST_EMAIL)
                .username(TEST_USERNAME)
                .nickname(TEST_USERNAME)
                .isVerified(isVerified)
                .build();
    }
}