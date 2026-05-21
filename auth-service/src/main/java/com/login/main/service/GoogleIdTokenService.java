package com.login.main.service;

import com.login.main.common.result.Result;

/**
 * Google ID Token 驗證服務介面
 *
 * 定義驗證 One Tap  登入回傳 ID Token 的核心操作。
 * 實作類別負責使用 GoogleIdTokenVerifier 向 Google 確認 Token 真偽，
 * 並從 payload 提取使用者基本資訊。
 */
public interface GoogleIdTokenService {

    /**
     * 驗證 Google ID Token 並提取使用者資訊
     *
     * @param idToken GIS One Tap 回傳的 ID Token 字串
     * @return 驗證成功時回傳含 sub、email、name 的 GoogleUserInfo；
     *         驗證失敗時回傳 TOKEN_INVALID 錯誤
     */
    Result<GoogleUserInfo> verify(String idToken);

    /**
     * Google 使用者資訊 Record
     *
     * @param sub   Google 使用者唯一識別碼（作為 providerId）
     * @param email 使用者 Google 電子郵件
     * @param name  使用者 Google 顯示名稱
     */
    record GoogleUserInfo(String sub, String email, String name) {}
}