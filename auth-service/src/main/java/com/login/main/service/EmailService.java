package com.login.main.service;

import com.login.main.enums.RedisKeyPrefix;

/**
 * 郵件發送服務介面
 *
 * 定義系統對外發送郵件的能力。
 * 支援發送 OTP 驗證碼信件，透過 {@link RedisKeyPrefix} 區分不同業務場景的郵件內容。
 */
public interface EmailService {

    /**
     * 發送 OTP 驗證碼郵件（依業務場景切換郵件主旨與內文）
     *
     * @param to        收件人電子郵件地址
     * @param otp       6 位數一次性驗證碼
     * @param keyPrefix 決定郵件內容的業務場景前綴（如密碼重設、Email 驗證）
     */
    void sendOtpEmail(String to, String otp, RedisKeyPrefix keyPrefix);

    /**
     * 發送含驗證連結的 Email 驗證信
     *
     * @param to              收件人電子郵件地址
     * @param verificationUrl 使用者點擊後可完成驗證的完整 URL
     */
    void sendVerificationLinkEmail(String to, String verificationUrl);
}