package com.login.main.util;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * HTTP Cookie 操作工具類
 * 
 * 提供關於安全 Cookie 的創建與刪除邏輯，主要用於管理儲存在客戶端瀏覽器的 Refresh Token。
 */
@Component
public class HttpCookieUtil {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/auth/refresh";
    // 7 天 (秒為單位)
    private static final int REFRESH_TOKEN_EXPIRATION = 7 * 24 * 60 * 60;

    /**
     * 建立刷新權杖 Cookie
     * 
     * 生成一個 HttpOnly 的安全 Cookie，路徑限制在 /auth/refresh 以提高安全性。
     * @param refreshToken 刷新權杖字串
     * @return 封裝好的 ResponseCookie 物件
     */
    public static ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false) // 開發環境為 false，正式環境建議為 true
                .path(COOKIE_PATH)
                .maxAge(REFRESH_TOKEN_EXPIRATION)
                .build();
    }

    /**
     * 建立邏輯刪除 Cookie
     * 
     * 建立一個 Max-Age 為 0 的同名 Cookie，通知瀏覽器立即刪除該權杖。
     * @return 刪除用 ResponseCookie 物件
     */
    public static ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
