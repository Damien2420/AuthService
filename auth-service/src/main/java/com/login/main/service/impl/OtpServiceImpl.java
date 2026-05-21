package com.login.main.service.impl;

import com.login.main.common.result.Result;
import com.login.main.enums.RedisKeyPrefix;
import com.login.main.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * OTP 驗證碼通用服務實作
 *
 * 負責 OTP 的生成（SecureRandom）、Redis 儲存與驗證邏輯。
 * 各業務場景透過 {@link RedisKeyPrefix} 指定對應的 key 前綴與 TTL，
 * 實作本身不感知業務語意，僅處理 OTP 的生命週期。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 生成 6 位數字 OTP 並存入 Redis
     *
     * @param identifier 識別符（通常為 email）
     * @param keyPrefix  Redis key 前綴，決定 key 命名空間與 TTL
     * @return 生成的 6 位數字 OTP 字串
     */
    @Override
    public String issueOtp(String identifier, RedisKeyPrefix keyPrefix) {
        // 使用 SecureRandom 確保隨機性，補零左填充至 6 位
        SecureRandom random = new SecureRandom();
        String otp = String.format("%06d", random.nextInt(1_000_000));

        String key = keyPrefix.buildKey(identifier);
        long ttl = keyPrefix.getTtlMinutes();
        redisTemplate.opsForValue().set(key, otp, ttl, TimeUnit.MINUTES);

        log.debug("OTP 已存入 Redis - Key: {}, TTL: {} 分鐘", key, ttl);
        return otp;
    }

    /**
     * 驗證 OTP 並一次性消費
     *
     * @param identifier 識別符（通常為 email）
     * @param otp        使用者輸入的 OTP
     * @param keyPrefix  Redis key 前綴
     * @return 驗證成功回傳 {@code Result.success()}，OTP 不存在或不匹配回傳 {@code Result.fail()}
     */
    @Override
    public Result<Void> verifyAndConsume(String identifier, String otp, RedisKeyPrefix keyPrefix) {
        String key = keyPrefix.buildKey(identifier);
        Object storedOtp = redisTemplate.opsForValue().get(key);

        // OTP 不存在（已過期）或不匹配
        if (storedOtp == null || !storedOtp.toString().equals(otp)) {
            log.warn("OTP 驗證失敗 - Key: {}, 原因: {}", key, storedOtp == null ? "OTP 已過期" : "OTP 不匹配");
            return Result.fail("OTP invalid or expired");
        }

        // 驗證通過立即刪除
        redisTemplate.delete(key);
        log.debug("OTP 驗證成功並已消費 - Key: {}", key);

        return Result.success();
    }
}
