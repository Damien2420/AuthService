package com.login.main.service;

import com.login.main.common.result.Result;

/**
 * OAuth2 一次性授權碼服務介面
 *
 * 負責管理 OAuth2 One-time Code Exchange 流程中的一次性授權碼。
 * 授權碼以 UUID 形式發放，存入 Redis 並設定短效 TTL，由前端憑碼換取 JWT Token。
 */
public interface OAuth2CodeService {

    /**
     * 為指定使用者發放一次性 OAuth2 授權碼，存入 Redis，TTL 60 秒。
     *
     * @param username 已通過 OAuth2 認證的使用者名稱
     * @return 成功回傳 UUID 格式的一次性授權碼；Redis 寫入異常則回傳 INTERNAL_ERROR 錯誤
     */
    Result<String> issueCode(String username);

    /**
     * 原子性讀取並刪除授權碼，回傳對應使用者名稱。
     * 授權碼一旦被消費即從 Redis 中刪除，確保只能使用一次。
     *
     * @param code 前端傳入的一次性授權碼
     * @return 成功回傳對應使用者名稱；碼不存在或已過期則回傳 OAUTH2_CODE_INVALID 錯誤
     */
    Result<String> consumeCode(String code);
}
