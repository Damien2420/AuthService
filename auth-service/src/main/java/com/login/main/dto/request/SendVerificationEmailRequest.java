package com.login.main.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 發送 Email 驗證碼請求 DTO
 *
 * 用於 send-verification 端點，
 * 承載使用者欲驗證的電子郵件地址。
 */
@Data
@Schema(description = "發送 Email 驗證碼請求")
public class SendVerificationEmailRequest {

    @NotBlank(message = "電子郵件不可為空")
    @Email(message = "電子郵件格式不正確")
    @Schema(description = "欲驗證的電子郵件地址", example = "user@example.com")
    private String email;
}
