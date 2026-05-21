package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TurnstileServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppProperties.Turnstile turnstileConfig;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TurnstileServiceImpl turnstileService;

    private static final String TEST_TOKEN = "valid-turnstile-token";
    private static final String TEST_SECRET = "test-secret-key";

    // ===== 驗證停用 =====

    @Test
    void verify_whenDisabled_shouldReturnSuccessWithoutCallingApi() {
        when(appProperties.getTurnstile()).thenReturn(turnstileConfig);
        when(turnstileConfig.isEnabled()).thenReturn(false);

        Result<Void> result = turnstileService.verify(TEST_TOKEN);

        assertThat(result.isSuccess()).isTrue();
        verify(restTemplate, never()).postForObject(anyString(), any(), eq(Map.class));
    }

    // ===== 驗證成功 =====

    @Test
    void verify_whenSuccess_shouldReturnSuccess() {
        when(appProperties.getTurnstile()).thenReturn(turnstileConfig);
        when(turnstileConfig.isEnabled()).thenReturn(true);
        when(turnstileConfig.getSecretKey()).thenReturn(TEST_SECRET);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("success", true));

        Result<Void> result = turnstileService.verify(TEST_TOKEN);

        assertThat(result.isSuccess()).isTrue();
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    // ===== 驗證失敗：API 回應為 null =====

    @Test
    void verify_whenResponseIsNull_shouldReturnFail() {
        when(appProperties.getTurnstile()).thenReturn(turnstileConfig);
        when(turnstileConfig.isEnabled()).thenReturn(true);
        when(turnstileConfig.getSecretKey()).thenReturn(TEST_SECRET);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(null);

        Result<Void> result = turnstileService.verify(TEST_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_FAILED);
    }

    // ===== 驗證失敗：success=false =====

    @Test
    void verify_whenSuccessIsFalse_shouldReturnFail() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error-codes", List.of("invalid-input-response"));

        when(appProperties.getTurnstile()).thenReturn(turnstileConfig);
        when(turnstileConfig.isEnabled()).thenReturn(true);
        when(turnstileConfig.getSecretKey()).thenReturn(TEST_SECRET);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(response);

        Result<Void> result = turnstileService.verify(TEST_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_FAILED);
    }

    // ===== 驗證失敗：API 拋出例外 =====

    @Test
    void verify_whenApiThrowsException_shouldReturnFail() {
        when(appProperties.getTurnstile()).thenReturn(turnstileConfig);
        when(turnstileConfig.isEnabled()).thenReturn(true);
        when(turnstileConfig.getSecretKey()).thenReturn(TEST_SECRET);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Network error"));

        Result<Void> result = turnstileService.verify(TEST_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_FAILED);
    }

    // ===== 驗證成功：token 為 null =====

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void verify_whenTokenIsNull_shouldSendEmptyResponseFieldAndReturnSuccess() {
        when(appProperties.getTurnstile()).thenReturn(turnstileConfig);
        when(turnstileConfig.isEnabled()).thenReturn(true);
        when(turnstileConfig.getSecretKey()).thenReturn(TEST_SECRET);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("success", true));

        Result<Void> result = turnstileService.verify(null);

        assertThat(result.isSuccess()).isTrue();

        
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(Map.class));
        Map<String, String> body = (Map<String, String>) requestCaptor.getValue().getBody();
        assertThat(body).containsEntry("response", "");
        assertThat(body).containsEntry("secret", TEST_SECRET);
    }
}