package com.login.main.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.login.main.service.TokenBlacklistService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名單服務實作
 *
 * 使用 Redis 儲存已吊銷的 Token，Key 為 Token 的 SHA-256 Hash，TTL 為 Token 剩餘有效時間。
 * Key 格式：blacklist:{SHA-256(token)}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:";
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 將 Token 加入黑名單，TTL 設為 Token 剩餘有效時間
     * Redis 寫入失敗時僅 log error，不拋出例外（fail-open for write）
     *
     * @param token      需要被吊銷的 JWT 字串
     * @param expiration Token 的過期時間
     */
    @Override
    public void blacklist(String token, Date expiration) {
        long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds <= 0) {
            log.debug("Token 已過期，無需加入黑名單");
            return;
        }
        try {
            String key = buildKey(token);
            redisTemplate.opsForValue().set(key, "revoked", ttlSeconds, TimeUnit.SECONDS);
            log.debug("Token 已加入黑名單，TTL: {}s", ttlSeconds);
        } catch (Exception e) {
            log.error("加入 Token 黑名單失敗（Redis 寫入異常），此次舊 Token 將不被吊銷: {}", e.getMessage());
        }
    }

    /**
     * 檢查 Token 是否已在黑名單中
     * Redis 查詢失敗時拋出 RuntimeException（fail-closed for read）
     *
     * @param token 需要驗證的 JWT 字串
     * @return 若已被吊銷則回傳 true
     * @throws RuntimeException 當 Redis 連線異常時拋出
     */
    @Override
    public boolean isBlacklisted(String token) {
        try {
            String key = buildKey(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("查詢 Token 黑名單失敗（Redis 讀取異常）: {}", e.getMessage());
            throw new RuntimeException("黑名單服務暫時不可用", e);
        }
    }

    /**
     * 以 SHA-256 計算 Token 的黑名單 Redis Key
     *
     * @param token JWT 字串
     * @return Redis Key 字串
     */
    private String buildKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return KEY_PREFIX + hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 演算法不可用", e);
        }
    }
}
