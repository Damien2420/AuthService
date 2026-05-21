package com.login.main.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 忘記密碼請求 DTO
 *
 * 承載使用者發起密碼重設流程時提供的電子郵件地址。
 * 系統將驗證帳號存在後，發送 6 位數 OTP 至該信箱。
 */
@Data
@Schema(description = "忘記密碼請求")
public class ForgotPasswordRequest {

    @NotBlank(message = "電子郵件不可為空")
    @Email(message = "電子郵件格式不正確")
    @Schema(description = "使用者電子郵件", example = "user@example.com")
    private String email;
}