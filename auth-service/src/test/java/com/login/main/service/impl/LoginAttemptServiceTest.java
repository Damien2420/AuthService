package com.login.main.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
public class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

    private static final String TEST_USERNAME = "testuser";

    @Test
    void recordFailedAttempt_firstFailure_shouldSetTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        loginAttemptService.recordFailedAttempt(TEST_USERNAME);

        verify(stringRedisTemplate, times(1)).expire(anyString(), eq(15L), eq(TimeUnit.MINUTES));
    }

    @Test
    void recordFailedAttempt_subsequentFailure_shouldNotSetTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        loginAttemptService.recordFailedAttempt(TEST_USERNAME);

        verify(stringRedisTemplate, never()).expire(anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void recordFailedAttempt_whenRedisThrows_shouldNotPropagateException() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis 掛了"));

        assertThatCode(() -> loginAttemptService.recordFailedAttempt(TEST_USERNAME))
            .doesNotThrowAnyException();
    }

    @Test
    void isLocked_whenNoRecord_shouldReturnFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        boolean result = loginAttemptService.isLocked(TEST_USERNAME);

        assertThat(result).isFalse();
    }

    @Test
    void isLocked_whenBelowThreshold_shouldReturnFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("4");

        boolean result = loginAttemptService.isLocked(TEST_USERNAME);

        assertThat(result).isFalse();
    }

    @Test
    void isLocked_whenAtThreshold_shouldReturnTrue() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("5");

        boolean result = loginAttemptService.isLocked(TEST_USERNAME);

        assertThat(result).isTrue();
    }

    @Test
    void isLocked_whenRedisThrows_shouldReturnFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis 掛了"));

        boolean result = loginAttemptService.isLocked(TEST_USERNAME);

        assertThat(result).isFalse();
    }

    @Test
    void isLocked_whenValueIsNonNumeric_shouldReturnFalse() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("not_a_number");

        boolean result = loginAttemptService.isLocked(TEST_USERNAME);

        assertThat(result).isFalse();
    }

    @Test
    void clearAttempts_shouldCallDelete() {
        loginAttemptService.clearAttempts(TEST_USERNAME);

        verify(stringRedisTemplate, times(1)).delete(anyString());
    }

    @Test
    void clearAttempts_whenRedisThrows_shouldNotPropagateException() {
        when(stringRedisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis 掛了"));

        assertThatCode(() -> loginAttemptService.clearAttempts(TEST_USERNAME))
            .doesNotThrowAnyException();
    }
}