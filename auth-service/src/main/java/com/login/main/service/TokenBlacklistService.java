package com.login.main.service;

import java.util.Date;

/**
 * Token 黑名單服務介面
 *
 * 管理已被吊銷的 JWT Token，底層以 Redis 持久化並依 TTL 自動清理。
 * 適用於 Refresh Token Rotation（吊銷舊 Refresh Token）及 Logout（吊銷 Access Token）。
 */
public interface TokenBlacklistService {

    /**
     * 將 Token 加入黑名單
     *
     * @param token      需要被吊銷的 JWT 字串
     * @param expiration Token 的過期時間，用於計算 Redis TTL
     */
    void blacklist(String token, Date expiration);

    /**
     * 檢查 Token 是否已在黑名單中
     *
     * @param token 需要驗證的 JWT 字串
     * @return 若已被吊銷則回傳 true
     * @throws RuntimeException 當 Redis 連線異常時拋出
     */
    boolean isBlacklisted(String token);
}
