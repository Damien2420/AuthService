package com.login.main.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
public class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    private static final String TEST_TOKEN = "test.jwt.token";

    @Test
    void blacklist_withValidToken_shouldCallRedisSet() {
        Date expiration = new Date(System.currentTimeMillis() + 60000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.blacklist(TEST_TOKEN, expiration);

        verify(valueOperations, times(1)).set(anyString(), eq("revoked"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void blacklist_withExpiredToken_shouldNotCallRedis() {
        Date expiration = new Date(System.currentTimeMillis() - 1000L);

        tokenBlacklistService.blacklist(TEST_TOKEN, expiration);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void blacklist_whenRedisThrows_shouldNotPropagateException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis 掛了")).when(valueOperations).set(anyString(), eq("revoked"), anyLong(),eq(TimeUnit.SECONDS));

        Date expiration = new Date(System.currentTimeMillis() + 60000L);

        assertThatCode(() -> tokenBlacklistService.blacklist(TEST_TOKEN, expiration)).doesNotThrowAnyException();
    }

    @Test
    void isBlacklisted_whenKeyExists_shouldReturnTrue() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean result = tokenBlacklistService.isBlacklisted(TEST_TOKEN);

        assertThat(result).isTrue();
    }

    @Test
    void isBlacklisted_whenRedisThrows_shouldThrowRuntimeException() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis error"));

        assertThatThrownBy(() -> tokenBlacklistService.isBlacklisted(TEST_TOKEN)).isInstanceOf(RuntimeException.class);
    }
}
