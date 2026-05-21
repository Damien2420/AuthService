package com.login.main.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.login.main.enums.RedisKeyPrefix;
import com.login.main.service.LoginAttemptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 帳號登入嘗試追蹤服務實作
 *
 * 使用 Redis 記錄登入失敗次數，採固定視窗策略（Fixed Window）：
 * 從第一次失敗起計時，視窗內累積失敗達門檻即鎖定，視窗到期自動解鎖。
 *
 * Redis Key 格式：{@link RedisKeyPrefix#LOGIN_FAIL} + username
 * Redis Value：失敗次數（String 格式整數）
 * 鎖定門檻：{@value #MAX_ATTEMPTS} 次
 * 視窗長度：由 {@link RedisKeyPrefix#LOGIN_FAIL} 的 TTL 決定（15 分鐘）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** 觸發鎖定的失敗次數門檻 */
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 記錄登入失敗
     *
     * @param username 登入失敗的使用者名稱
     */
    @Override
    public void recordFailedAttempt(String username) {
        try {
            String key = RedisKeyPrefix.LOGIN_FAIL.buildKey(username);
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, RedisKeyPrefix.LOGIN_FAIL.getTtlMinutes(), TimeUnit.MINUTES);
            }
            log.warn("帳號登入失敗記錄 - 使用者: {}, 累計失敗次數: {}/{}", username, count, MAX_ATTEMPTS);
        } catch (Exception e) {
            log.warn("記錄登入失敗次數時 Redis 發生異常 - 使用者: {}, 錯誤: {}", username, e.getMessage());
        }
    }

    /**
     * 判斷帳號是否處於鎖定狀態
     *
     * @param username 欲查詢的使用者名稱
     * @return true 表示帳號已鎖定，false 表示可正常登入
     */
    @Override
    public boolean isLocked(String username) {
        try {
            String key = RedisKeyPrefix.LOGIN_FAIL.buildKey(username);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return false;
            }
            int count = Integer.parseInt(value);
            return count >= MAX_ATTEMPTS;
        } catch (Exception e) {
            log.warn("查詢帳號鎖定狀態時 Redis 發生異常 - 使用者: {}, 錯誤: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * 清除帳號的登入失敗記錄
     *
     * @param username 登入成功的使用者名稱
     */
    @Override
    public void clearAttempts(String username) {
        try {
            String key = RedisKeyPrefix.LOGIN_FAIL.buildKey(username);
            stringRedisTemplate.delete(key);
            log.debug("帳號登入失敗記錄已清除 - 使用者: {}", username);
        } catch (Exception e) {
            log.warn("清除登入失敗記錄時 Redis 發生異常 - 使用者: {}, 錯誤: {}", username, e.getMessage());
        }
    }
}
