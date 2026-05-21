package com.login.main.service;

import com.login.main.common.result.Result;

import java.util.Date;

/**
 * 密碼重設服務介面
 *
 */
public interface PasswordResetService {

    /**
     * 發送 OTP 驗證碼至使用者信箱
     *
     * @param email 使用者電子郵件
     * @return 成功時回傳 Result.success()，帳號不存在時回傳 USER_NOT_FOUND
     */
    Result<Void> sendOtp(String email);

    /**
     * 驗證 OTP 並核發 Reset Token
     *
     * @param email 使用者電子郵件
     * @param otp   使用者輸入的 6 位數驗證碼
     * @return 成功時回傳含 Reset Token 的 Result，失敗時回傳 OTP_INVALID
     */
    Result<String> verifyOtp(String email, String otp);

    /**
     * 重設密碼並使所有現有 Session 失效
     *
     * @param resetToken  由 verifyOtp 核發的一次性重設權杖
     * @param newPassword 新密碼（明文，方法內部將進行 BCrypt 加密）
     * @return 成功時回傳 Result.success()，Token 無效時回傳 RESET_TOKEN_INVALID
     */
    Result<Void> resetPassword(String resetToken, String newPassword);

    /**
     * 檢查指定使用者的 JWT Token 是否在密碼重設後已失效
     *
     * 透過比對 Token 的 issuedAt 與 Redis 中儲存的密碼重設時間戳，
     * 判斷該 Token 是否為密碼重設前發出的舊 Token。
     *
     * @param username 使用者名稱（JWT subject）
     * @param issuedAt Token 的發行時間
     * @return 若 Token 在重設前發出則回傳 true，否則回傳 false
     * @throws RuntimeException 當 Redis 查詢異常時拋出
     */
    boolean isTokenInvalidatedByPasswordReset(String username, Date issuedAt);
}