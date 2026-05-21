package com.login.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 使用者個人資料回應 DTO
 *
 * 回傳目前登入使用者的基本資料，包含帳號資訊與登入提供者清單。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    /** 使用者名稱 */
    private String username;

    /** 電子郵件 */
    private String email;

    /** 顯示暱稱 */
    private String nickname;

    /** 是否已完成 Email 驗證 */
    private boolean isVerified;

    /**
     * 登入提供者清單
     * 包含 LOCAL（帳號密碼）及已綁定的 OAuth2 提供者（如 GOOGLE、DISCORD、LINE）
     */
    private List<String> providers;

    /** 帳號建立時間 */
    private LocalDateTime createdAt;
}
