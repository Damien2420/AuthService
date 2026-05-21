package com.login.main.service.impl;

import com.login.main.common.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuth2CodeServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OAuth2CodeServiceImpl oAuth2CodeService;

    private static final String TEST_USERNAME = "testuser";

    // ===== issueCode =====

    @Test
    void issueCode_whenRedisWriteSuccess_shouldReturnCode() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        Result<String> result = oAuth2CodeService.issueCode(TEST_USERNAME);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull().isNotEmpty();
        verify(valueOperations, times(1)).set(anyString(), eq(TEST_USERNAME), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void issueCode_whenRedisWriteFails_shouldReturnFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        Result<String> result = oAuth2CodeService.issueCode(TEST_USERNAME);

        assertThat(result.isFailed()).isTrue();
    }

    // ===== consumeCode =====

    @Test
    void consumeCode_whenCodeValid_shouldReturnUsername() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn(TEST_USERNAME);

        Result<String> result = oAuth2CodeService.consumeCode("some-code");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(TEST_USERNAME);
    }

    @Test
    void consumeCode_whenCodeInvalid_shouldReturnFail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        Result<String> result = oAuth2CodeService.consumeCode("invalid-code");

        assertThat(result.isFailed()).isTrue();
    }
}
