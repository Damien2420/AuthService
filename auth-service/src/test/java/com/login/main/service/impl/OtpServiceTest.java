package com.login.main.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.login.main.common.result.Result;
import com.login.main.enums.RedisKeyPrefix;

@ExtendWith(MockitoExtension.class)
public class OtpServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private OtpServiceImpl otpService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final RedisKeyPrefix TEST_KEY_PREFIX = RedisKeyPrefix.OTP_EMAIL_VERIFICATION;

    @Test
    void issueOtp_shouldReturnSixDigitNumericString() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String otp = otpService.issueOtp(TEST_EMAIL, TEST_KEY_PREFIX);

        assertThat(otp).matches("\\d{6}");
    }

    @Test
    void issueOtp_shouldStoreOtpInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpService.issueOtp(TEST_EMAIL, TEST_KEY_PREFIX);

        verify(valueOperations, times(1)).set(
                contains(TEST_KEY_PREFIX.getPrefix()),
                anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void verifyAndConsume_withCorrectOtp_shouldReturnSuccess() {
        String otp = "123456";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(otp);

        Result<Void> result = otpService.verifyAndConsume(TEST_EMAIL, otp, TEST_KEY_PREFIX);

        assertThat(result.isSuccess()).isTrue();
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    void verifyAndConsume_whenOtpNotFound_shouldReturnFail() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Result<Void> result = otpService.verifyAndConsume(TEST_EMAIL, "123456", TEST_KEY_PREFIX);

        assertThat(result.isFailed()).isTrue();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verifyAndConsume_withWrongOtp_shouldReturnFail() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("999999");

        Result<Void> result = otpService.verifyAndConsume(TEST_EMAIL, "123456", TEST_KEY_PREFIX);

        assertThat(result.isFailed()).isTrue();
        verify(redisTemplate, never()).delete(anyString());
    }
}
