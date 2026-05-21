package com.login.main.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登入請求 DTO
 * 
 * 承載使用者嘗試進行身分驗證時提供的憑證（帳號、密碼）。
 * 支援「記住我」選項，用以決定生成的 Refresh Token 有效期限。
 */
@Data
@Schema(description = "登入請求")
public class LoginRequest {
    @NotBlank(message = "使用者名稱不可為空")
    @Schema(description = "使用者名稱", example = "testuser")
    private String username;

    @NotBlank(message = "密碼不可為空")
    @Schema(description = "密碼", example = "password123")
    private String password;

    @Schema(description = "記住我", example = "true")
    private boolean rememberMe;

    @Schema(description = "Cloudflare Turnstile 驗證 Token")
    private String turnstileToken;
}
