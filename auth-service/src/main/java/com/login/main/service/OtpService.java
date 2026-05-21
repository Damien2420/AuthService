package com.login.main.service;

import com.login.main.common.result.Result;
import com.login.main.enums.RedisKeyPrefix;

/**
 * OTP 驗證碼通用服務介面
 *
 * 提供跨業務場景的 OTP 生成、Redis 儲存與驗證邏輯。
 * 透過 {@link RedisKeyPrefix} 區分不同用途的 OTP，確保 key 空間隔離。
 */
public interface OtpService {

    /**
     * 生成 6 位數字 OTP 並存入 Redis
     *
     * 若指定 key 已存在舊的 OTP，將直接覆寫（允許重送）。
     * TTL 由 {@link RedisKeyPrefix#getTtlMinutes()} 決定。
     *
     * @param identifier 識別符（通常為 email）
     * @param keyPrefix  Redis key 前綴，決定 key 命名空間與 TTL
     * @return 生成的 6 位數字 OTP 字串
     */
    String issueOtp(String identifier, RedisKeyPrefix keyPrefix);

    /**
     * 驗證 OTP 並原子性地消費（一次性）
     *
     * 驗證通過後立即從 Redis 刪除，防止重複使用。
     * OTP 不存在（已過期）或不匹配時回傳失敗，由呼叫方決定對應的 ErrorCode。
     *
     * @param identifier 識別符（通常為 email）
     * @param otp        使用者輸入的 OTP
     * @param keyPrefix  Redis key 前綴
     * @return 驗證成功回傳 {@code Result.success()}，失敗回傳 {@code Result.fail(boolean)}
     */
    Result<Void> verifyAndConsume(String identifier, String otp, RedisKeyPrefix keyPrefix);
}
