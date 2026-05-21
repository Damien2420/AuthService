package com.login.main.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 註冊請求 DTO
 * 
 * 承載使用者註冊時提交的原始資料，包含電子郵件、帳號與密碼。
 * 結合 Bean Validation 確保輸入資料的格式與長度符合安全性要求。
 */
@Data
@Schema(description = "註冊請求")
public class RegisterRequest {
    
    @NotBlank(message = "電子郵件不可為空")
    @Email(message = "電子郵件格式不正確")
    @Schema(description = "電子郵件", example = "user@example.com")
    private String email;

    @NotBlank(message = "使用者名稱不可為空")
    @Size(min = 3, max = 20, message = "使用者名稱長度需在 3 到 20 個字元之間")
    @Schema(description = "使用者名稱", example = "newuser")
    private String username;

    @NotBlank(message = "密碼不可為空")
    @Size(min = 6, max = 20, message = "密碼長度需在 6 到 20 個字元之間")
    @Schema(description = "密碼", example = "password123")
    private String password;

    @NotBlank(message = "請輸入驗證碼")
    @Schema(description = "Email 驗證碼（6 位數字）", example = "123456")
    private String verificationCode;

    @Schema(description = "Cloudflare Turnstile 驗證 Token")
    private String turnstileToken;
}
