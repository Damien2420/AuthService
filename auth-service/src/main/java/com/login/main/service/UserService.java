package com.login.main.service;

import com.login.main.common.result.Result;
import com.login.main.dto.response.UserEmailInfoDTO;
import com.login.main.dto.response.UserProfileResponse;

/**
 * 使用者個人資料服務介面
 *
 * 定義與使用者個人資料相關的業務邏輯，包含查詢、更新暱稱與更新密碼。
 */
public interface UserService {

    /**
     * 取得使用者個人資料
     *
     * 根據使用者名稱查詢個人資料，包含登入提供者清單。
     * @param username 使用者名稱
     * @return 包含 UserProfileResponse 的 Result
     */
    Result<UserProfileResponse> getProfile(String username);

    /**
     * 以 Email 查詢使用者基本資訊。
     *
     * 僅回傳已驗證且未被封鎖的使用者，供邀請成員等跨服務流程使用。
     * @param email 電子郵件
     * @return 包含 UserEmailInfoDTO 的 Result，找不到時回傳失敗
     */
    Result<UserEmailInfoDTO> searchByEmail(String email);

    /**
     * 更新使用者暱稱
     *
     * @param username 使用者名稱
     * @param nickname 新暱稱
     * @return 操作結果
     */
    Result<Void> updateNickname(String username, String nickname);

    /**
     * 更新使用者密碼
     *
     * OAuth2 使用者首次設定密碼時 currentPassword 可為 null；
     * 已有密碼的使用者必須提供正確的現有密碼。
     * @param username        使用者名稱
     * @param currentPassword 目前密碼（OAuth2 使用者首次設定時傳 null）
     * @param newPassword     新密碼
     * @return 操作結果
     */
    Result<Void> updatePassword(String username, String currentPassword, String newPassword);
}
