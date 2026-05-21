package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private AppProperties appProperties;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private EmailVerificationServiceImpl emailVerificationService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_OTP = "123456";
    private static final String TEST_FRONTEND_URL = "https://example.com";

    // ===== sendVerificationOtpEmail =====

    @Test
    void sendVerificationOtpEmail_whenEmailNotExists_shouldReturnSuccess() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(otpService.issueOtp(anyString(), eq(RedisKeyPrefix.OTP_EMAIL_VERIFICATION))).thenReturn(TEST_OTP);

        Result<Void> result = emailVerificationService.sendVerificationOtpEmail(TEST_EMAIL);

        assertThat(result.isSuccess()).isTrue();
        verify(otpService, times(1)).issueOtp(anyString(), eq(RedisKeyPrefix.OTP_EMAIL_VERIFICATION));
        verify(emailService, times(1)).sendOtpEmail(anyString(), anyString(), eq(RedisKeyPrefix.OTP_EMAIL_VERIFICATION));
    }

    @Test
    void sendVerificationOtpEmail_whenEmailAlreadyExists_shouldReturnFail() {
        User existingUser = User.builder().email(TEST_EMAIL).username(TEST_USERNAME).nickname(TEST_USERNAME).build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(existingUser));

        Result<Void> result = emailVerificationService.sendVerificationOtpEmail(TEST_EMAIL);

        assertThat(result.isFailed()).isTrue();
        verify(otpService, never()).issueOtp(anyString(), any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), any());
    }

    // ===== verifyAndConsume =====

    @Test
    void verifyAndConsume_whenOtpValid_shouldReturnSuccess() {
        when(otpService.verifyAndConsume(anyString(), anyString(), eq(RedisKeyPrefix.OTP_EMAIL_VERIFICATION)))
                .thenReturn(Result.success());

        Result<Void> result = emailVerificationService.verifyAndConsume(TEST_EMAIL, TEST_OTP);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void verifyAndConsume_whenOtpInvalid_shouldReturnFail() {
        when(otpService.verifyAndConsume(anyString(), anyString(), eq(RedisKeyPrefix.OTP_EMAIL_VERIFICATION)))
                .thenReturn(Result.fail(ErrorCode.EMAIL_VERIFICATION_OTP_INVALID));

        Result<Void> result = emailVerificationService.verifyAndConsume(TEST_EMAIL, TEST_OTP);

        assertThat(result.isFailed()).isTrue();
    }

    // ===== resendVerificationLink =====

    @Test
    void resendVerificationLink_whenValid_shouldReturnSuccess() {
        User user = User.builder()
                .ID(UUID.randomUUID())
                .email(TEST_EMAIL)
                .username(TEST_USERNAME)
                .nickname(TEST_USERNAME)
                .isVerified(false)
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(appProperties.getFrontendUrl()).thenReturn(TEST_FRONTEND_URL);

        Result<Void> result = emailVerificationService.resendVerificationLink(TEST_USERNAME);

        assertThat(result.isSuccess()).isTrue();
        verify(emailService, times(1)).sendVerificationLinkEmail(anyString(), anyString());
        verify(stringRedisTemplate, times(1)).expire(anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void resendVerificationLink_whenRateLimitExceeded_shouldReturnFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(4L);

        Result<Void> result = emailVerificationService.resendVerificationLink(TEST_USERNAME);

        assertThat(result.isFailed()).isTrue();
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void resendVerificationLink_whenUserNotFound_shouldReturnFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        Result<Void> result = emailVerificationService.resendVerificationLink(TEST_USERNAME);

        assertThat(result.isFailed()).isTrue();
        verify(emailService, never()).sendVerificationLinkEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationLink_whenAlreadyVerified_shouldReturnFail() {
        User verifiedUser = User.builder()
                .ID(UUID.randomUUID())
                .email(TEST_EMAIL)
                .username(TEST_USERNAME)
                .nickname(TEST_USERNAME)
                .isVerified(true)
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(verifiedUser));

        Result<Void> result = emailVerificationService.resendVerificationLink(TEST_USERNAME);

        assertThat(result.isFailed()).isTrue();
        verify(emailService, never()).sendVerificationLinkEmail(anyString(), anyString());
    }

    // ===== verifyByToken =====

    @Test
    void verifyByToken_whenTokenValid_shouldReturnSuccess() {
        User user = User.builder()
                .ID(UUID.randomUUID())
                .email(TEST_EMAIL)
                .username(TEST_USERNAME)
                .nickname(TEST_USERNAME)
                .isVerified(false)
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        Result<Void> result = emailVerificationService.verifyByToken("some-token");

        assertThat(result.isSuccess()).isTrue();
        assertThat(user.isVerified()).isTrue();
        verify(userRepository, times(1)).save(user);
        verify(stringRedisTemplate, times(1)).delete(anyString());
    }

    @Test
    void verifyByToken_whenTokenInvalid_shouldReturnFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Result<Void> result = emailVerificationService.verifyByToken("invalid-token");

        assertThat(result.isFailed()).isTrue();
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void verifyByToken_whenUserNotFound_shouldReturnFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Result<Void> result = emailVerificationService.verifyByToken("some-token");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_LINK_INVALID);
        verify(userRepository, never()).save(any());
    }
}
