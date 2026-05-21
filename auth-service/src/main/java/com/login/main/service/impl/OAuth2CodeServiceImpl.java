package com.login.main.service.impl;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.service.OAuth2CodeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 一次性授權碼服務實作
 *
 * 使用 Redis 儲存一次性授權碼與使用者名稱的對應關係。
 * 授權碼的有效期為 60 秒，透過 GETDEL 指令確保每組授權碼只能被消費一次。
 * Redis Key 格式：oauth2:code:{UUID}
 * Redis Value：使用者名稱 (String)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2CodeServiceImpl implements OAuth2CodeService {

    /** Redis Key 前綴 */
    private static final String CODE_KEY_PREFIX = "oauth2:code:";

    /** 授權碼有效期，單位：秒 */
    private static final long CODE_TTL_SECONDS = 60L;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 發放一次性 OAuth2 授權碼
     *
     * Redis 寫入失敗時採 fail-closed 策略：記錄 error log 並回傳 INTERNAL_ERROR，
     * 避免因授權碼未寫入成功而讓 URL 帶有無效 code 導向前端。
     *
     * @param username 使用者名稱
     * @return 成功回傳 UUID 格式的一次性授權碼；Redis 異常則回傳 INTERNAL_ERROR 錯誤
     */
    @Override
    public Result<String> issueCode(String username) {
        try {
            String code = UUID.randomUUID().toString();
            String key = CODE_KEY_PREFIX + code;
            stringRedisTemplate.opsForValue().set(key, username, CODE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("OAuth2 一次性授權碼已發放 - 使用者: {}", username);
            return Result.success(code);
        } catch (Exception e) {
            log.error("發放 OAuth2 授權碼失敗（Redis 寫入異常）- 使用者: {}, 錯誤: {}", username, e.getMessage());
            return Result.fail(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 消費一次性 OAuth2 授權碼（原子讀取並刪除）
     *
     * 使用 Redis GETDEL 指令，確保讀取與刪除的原子性，避免 code 被重複使用。
     *
     * @param code 前端傳入的一次性授權碼
     * @return 成功回傳使用者名稱；碼不存在或已過期回傳 OAUTH2_CODE_INVALID 錯誤
     */
    @Override
    public Result<String> consumeCode(String code) {
        String key = CODE_KEY_PREFIX + code;
        // getAndDelete 對應 Redis GETDEL 指令，原子性讀取並刪除，Key 不存在時回傳 null
        String username = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (username == null) {
            log.warn("OAuth2 授權碼無效或已過期 - code: {}", code);
            return Result.fail(ErrorCode.OAUTH2_CODE_INVALID);
        }
        log.debug("OAuth2 授權碼已消費 - 使用者: {}", username);
        return Result.success(username);
    }
}
