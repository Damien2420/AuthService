package com.login.main.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新密碼請求 DTO
 *
 * 用於 PATCH /api/v1/users/me/password。
 * currentPassword 為選填：OAuth2 使用者首次設定密碼時不需提供，
 * 已有密碼的使用者則必須提供正確的現有密碼才能修改。
 */
@Data
public class UpdatePasswordRequest {

    /**
     * 目前密碼（選填）
     * 已有本地密碼的使用者必須提供，OAuth2 使用者首次設定可省略。
     */
    private String currentPassword;

    /**
     * 新密碼
     * 不可為空白，長度至少 8 字元。
     */
    @NotBlank(message = "新密碼不可為空白")
    @Size(min = 8, message = "密碼長度至少需要 8 個字元")
    private String newPassword;
}
