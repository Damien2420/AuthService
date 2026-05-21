package com.login.main.service;

import com.login.main.common.result.Result;
import com.login.main.entity.SocialAccount;

/**
 * 社交帳號業務邏輯服務介面
 * 
 * 專責處理第三方社群帳號的查詢與關聯邏輯。
 */
public interface SocialAccountService {
    /**
     * 根據供應商標識符尋找社交帳號
     * 
     * 確認該第三方身分是否已存在於系統中，並返回關聯的實體。
     * @param providerID 第三方唯一 ID
     * @return 包含 SocialAccount 實體的 Result 物件
     */
    Result<SocialAccount> findByProviderID(String providerID);
}
