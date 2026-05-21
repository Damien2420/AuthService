package com.login.main.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 重設密碼請求 DTO
 *
 * 承載使用者在重設密碼頁面提交的一次性 Reset Token 與新密碼。
 * Reset Token 由 verify-otp 端點核發，有效期為 10 分鐘，且只能使用一次。
 */
@Data
@Schema(description = "重設密碼請求")
public class ResetPasswordRequest {

    @NotBlank(message = "重設權杖不可為空")
    @Schema(description = "由 OTP 驗證流程取得的一次性重設權杖")
    private String resetToken;

    @NotBlank(message = "新密碼不可為空")
    @Size(min = 6, max = 20, message = "密碼長度需在 6 到 20 個字元之間")
    @Schema(description = "新密碼（6-20 位英數字元）", example = "newPass123")
    private String newPassword;
}