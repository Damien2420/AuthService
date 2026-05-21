package com.login.main.service;

import com.login.main.common.result.Result;
import com.login.main.dto.internal.TokenInfo;
import com.login.main.entity.User;
import com.login.main.enums.Providers;

/**
 * 認證服務介面
 *
 * 定義與使用者認證生命週期相關的核心操作，包含帳號密碼登入、社交帳號登入、
 * 註冊、Token 換發、登出，以及社交媒體帳號連動。
 */
public interface AuthService {

    /**
     * 帳號密碼登入 - 生成 JWT Token
     *
     * 憑證驗證由 Controller 層的 AuthenticationManager 負責，
     * 本方法僅負責生成並回傳 AccessToken 與 RefreshToken。
     *
     * @param username   已通過驗證的使用者名稱
     * @param rememberMe 是否延長 RefreshToken 有效期
     * @return 包含 Access/Refresh Token 的 Result 物件
     */
    Result<TokenInfo> login(String username, boolean rememberMe);

    /**
     * Google GIS One Tap 登入
     *
     * 驗證 Google ID Token，查詢或自動建立對應帳號，最後生成並回傳 JWT Token。
     *
     * @param googleIdToken 前端透過 Google Identity Services 取得的 ID Token
     * @return 包含 Access/Refresh Token 的 Result 物件，Token 無效時回傳對應錯誤碼
     */
    Result<TokenInfo> loginWithGoogle(String googleIdToken);

    /**
     * 註冊新使用者
     *
     * 處理標準帳號註冊流程，先驗證 Email OTP，通過後建立帳號並返回登入 Token。
     *
     * @param email            使用者電子郵件
     * @param username         使用者名稱
     * @param password         原始密碼
     * @param verificationCode 使用者透過 Email 取得的 OTP 驗證碼
     * @return 包含 Access/Refresh Token 的 Result 物件，OTP 無效時回傳 EMAIL_VERIFICATION_OTP_INVALID
     */
    Result<TokenInfo> register(String email, String username, String password, String verificationCode);

    /**
     * 刷新訪問權杖
     *
     * 在 AccessToken 過期時，利用 RefreshToken 獲取新一組權杖。
     *
     * @param refreshToken 客戶端提供的刷新權杖
     * @return 包含新 Token 資訊的 Result 物件
     */
    Result<TokenInfo> refreshToken(String refreshToken);

    /**
     * 儲存/更新使用者
     *
     * 將 User 實體持久化至資料庫。
     *
     * @param user 使用者實體
     * @return 處理結果，包含更新後的 User
     */
    Result<User> save(User user);

    /**
     * 使用者登出
     *
     * 將 AccessToken 與 RefreshToken 加入黑名單，使其立即失效。
     * 採 fail-open 策略：即使 Redis 異常，登出仍視為成功（確保使用者體驗）。
     *
     * @param accessToken  需要吊銷的存取權杖（可為 null）
     * @param refreshToken 需要吊銷的刷新權杖（可為 null）
     * @return 永遠回傳成功的 Result
     */
    Result<Void> logout(String accessToken, String refreshToken);

    /**
     * 處理社交帳號登入與關聯
     *
     * 整合第三方 OAuth2 回傳資訊，實現自動註冊或現有帳號綁定。
     *
     * @param email      從第三方獲取的電子郵件
     * @param username   第三方暱稱
     * @param provider   社交平台供應商（如 GOOGLE、DISCORD、LINE）
     * @param providerId 第三方唯一身分標識
     * @return 處理結果，包含 User 實體
     */
    Result<User> registerSocialUser(String email, String username, Providers provider, String providerId);
}
