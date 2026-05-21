package com.login.main.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 驗證 OTP 請求 DTO
 *
 * 承載使用者提交的電子郵件與 6 位數一次性驗證碼。
 * 驗證成功後，系統將核發一次性 Reset Token 供後續密碼重設使用。
 */
@Data
@Schema(description = "OTP 驗證請求")
public class VerifyOtpRequest {

    @NotBlank(message = "電子郵件不可為空")
    @Email(message = "電子郵件格式不正確")
    @Schema(description = "使用者電子郵件", example = "user@example.com")
    private String email;

    @NotBlank(message = "驗證碼不可為空")
    @Size(min = 6, max = 6, message = "驗證碼必須為 6 位數")
    @Schema(description = "6 位數一次性驗證碼", example = "123456")
    private String otp;
}