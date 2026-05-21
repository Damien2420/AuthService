package com.login.main.service.impl;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.service.GoogleIdTokenService;

import lombok.extern.slf4j.Slf4j;

/**
 * Google ID Token 驗證服務實作
 *
 * 使用 google-api-client 的 GoogleIdTokenVerifier 驗證 GIS One Tap 回傳的 ID Token。
 * Google Client ID 直接從 OAuth2 設定中讀取，避免重複定義。
 */
@Slf4j
@Service
public class GoogleIdTokenServiceImpl implements GoogleIdTokenService {

    private final GoogleIdTokenVerifier verifier;

    /**
     * 建構子：初始化 GoogleIdTokenVerifier
     *
     * @param clientId Google OAuth2 Client ID（從 application.properties 注入）
     */
    public GoogleIdTokenServiceImpl(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    /**
     * 驗證 Google ID Token 並提取使用者資訊
     *
     * @param idToken GIS One Tap 回傳的 ID Token 字串
     * @return 驗證成功時回傳含 sub、email、name 的 GoogleUserInfo；
     *         驗證失敗時回傳 TOKEN_INVALID 錯誤
     */
    @Override
    public Result<GoogleUserInfo> verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                log.warn("Google ID Token 驗證失敗：Token 無效或已過期");
                return Result.fail(ErrorCode.TOKEN_INVALID);
            }

            GoogleIdToken.Payload payload = token.getPayload();
            String sub = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            log.debug("Google ID Token 驗證成功 - sub: {}, email: {}", sub, email);
            return Result.success(new GoogleUserInfo(sub, email, name));

        } catch (Exception e) {
            log.error("Google ID Token 驗證異常: {}", e.getMessage(), e);
            return Result.fail(ErrorCode.TOKEN_INVALID);
        }
    }
}