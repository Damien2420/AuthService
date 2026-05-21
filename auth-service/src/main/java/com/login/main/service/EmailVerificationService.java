package com.login.main.service;

import com.login.main.common.result.Result;

/**
 * Email 驗證服務介面
 *
 * 管理使用者註冊時的 Email 驗證流程。
 * 採用「先驗證再註冊」設計：使用者填入 Email 後先取得 OTP，
 * 提交註冊表單時一併驗證 OTP，通過後才建立帳號（帳號建立時 isVerified 直接為 true）。
 */
public interface EmailVerificationService {

    /**
     * 發送 Email 驗證 OTP 至指定信箱
     *
     * 若該 Email 已存在於系統中，回傳 {@code EMAIL_ALREADY_EXISTS} 錯誤，
     * 告知使用者此信箱已被使用，不需等到註冊時才發現。
     *
     * @param email 使用者填入的電子郵件地址
     * @return 成功時回傳 {@code Result.success()}，Email 已被使用時回傳對應失敗
     */
    Result<Void> sendVerificationOtpEmail(String email);

    /**
     * 驗證 Email OTP 並一次性消費
     *
     * @param email 使用者電子郵件地址
     * @param otp   使用者輸入的 6 位數驗證碼
     * @return 驗證成功回傳 {@code Result.success()}，OTP 無效或過期回傳 {@code EMAIL_VERIFICATION_OTP_INVALID}
     */
    Result<Void> verifyAndConsume(String email, String otp);

    /**
     * 重新寄出含驗證連結的 Email 驗證信
     *
     * 針對已註冊但尚未完成 Email 驗證的使用者，生成 UUID token 並以連結方式寄出驗證信。
     * 若帳號不存在回傳 {@code USER_NOT_FOUND}；若已驗證回傳 {@code EMAIL_ALREADY_VERIFIED}。
     *
     * @param username 使用者帳號名稱
     * @return 成功時回傳 {@code Result.success()}，失敗時回傳對應錯誤
     */
    Result<Void> resendVerificationLink(String username);

    /**
     * 驗證 Email 驗證連結中的 token 並完成帳號驗證
     *
     * 從 Redis 取出 token 對應的 email，找到使用者後標記為已驗證，並刪除 token 防止重複使用。
     * 若 token 不存在或已過期回傳 {@code VERIFICATION_LINK_INVALID}；使用者不存在回傳 {@code USER_NOT_FOUND}。
     *
     * @param token 驗證連結中的 UUID token
     * @return 成功時回傳 {@code Result.success()}，失敗時回傳對應錯誤
     */
    Result<Void> verifyByToken(String token);
}
