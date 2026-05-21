package com.login.main.service.impl;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
import com.login.main.service.TurnstileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cloudflare Turnstile 人機驗證服務實作
 *
 * 呼叫 Cloudflare siteverify API 驗證前端傳入的 Turnstile Token。
 * 支援 {@code app.turnstile.enabled=false} 旗標以在本地開發環境略過驗證。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurnstileServiceImpl implements TurnstileService {

    /** Cloudflare Turnstile siteverify API 端點 */
    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    /**
     * 驗證 Cloudflare Turnstile Token
     *
     * 若 {@code enabled=false}，略過驗證直接回傳成功。
     * 若呼叫 Cloudflare API 時發生網路異常，回傳 CAPTCHA_FAILED，避免異常狀態下放行未驗證的請求。
     *
     * @param token 前端 Turnstile Widget 產生的驗證 Token
     * @return 驗證通過回傳 {@code Result.success(null)}；
     *         驗證失敗或 API 異常回傳 {@code Result.fail(CAPTCHA_FAILED)}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result<Void> verify(String token) {
        if (!appProperties.getTurnstile().isEnabled()) {
            log.debug("Turnstile 驗證已停用，略過驗證");
            return Result.success();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of(
                    "secret", appProperties.getTurnstile().getSecretKey(),
                    "response", token != null ? token : ""
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(SITEVERIFY_URL, request, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                log.warn("Turnstile 驗證失敗 - errorCodes: {}", response != null ? response.get("error-codes") : "no response");
                return Result.fail(ErrorCode.CAPTCHA_FAILED);
            }

            log.debug("Turnstile 驗證通過");
            return Result.success();

        } catch (Exception e) {
            log.error("呼叫 Turnstile siteverify API 發生異常 - 錯誤: {}", e.getMessage());
            return Result.fail(ErrorCode.CAPTCHA_FAILED);
        }
    }
}
