package com.login.main.enums;

import jakarta.annotation.Nullable;

/**
 * Redis Key 前綴統一管理
 *
 * 集中定義系統中所有 Redis key 的前綴，避免各 Service 散落定義硬編碼字串。
 * 若 Key 具有固定 TTL（如 OTP），可透過 ttlMinutes 取得；TTL 動態決定的 Key 此欄位為 null。
 */
public enum RedisKeyPrefix {

    /** Email 驗證 OTP，TTL 5 分鐘 */
    OTP_EMAIL_VERIFICATION("email:verify:otp:", 5L),

    /** 密碼重設 OTP，TTL 5 分鐘 */
    OTP_PASSWORD_RESET("pwd_reset:otp:", 5L),

    /** 登入失敗計數，TTL 15 分鐘 */
    LOGIN_FAIL("login:fail:", 15L),

    /** Email 驗證連結 token，TTL 24 小時（1440 分鐘） */
    EMAIL_VERIFICATION_LINK("email:verify:link:", 1440L),

    /** Email 驗證連結重寄頻率限制計數，TTL 5 分鐘（每 5 分鐘最多 3 次） */
    EMAIL_RESEND_RATE_LIMIT("email:resend:limit:", 5L);


    private final String prefix;

    @Nullable
    private final Long ttlMinutes;

    RedisKeyPrefix(String prefix, @Nullable Long ttlMinutes) {
        this.prefix = prefix;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * 取得 Redis key 前綴字串
     *
     * @return key 前綴，例如 "email:verify:otp:"
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 取得固定 TTL（分鐘），若此 Key 無固定 TTL 則回傳 null
     *
     * @return TTL 分鐘數，或 null
     */
    @Nullable
    public Long getTtlMinutes() {
        return ttlMinutes;
    }

    /**
     * 組合完整 Redis Key
     *
     * @param identifier 識別符，例如 email 地址
     * @return 完整 Redis Key，例如 "email:verify:otp:user@example.com"
     */
    public String buildKey(String identifier) {
        return prefix + identifier;
    }
}
