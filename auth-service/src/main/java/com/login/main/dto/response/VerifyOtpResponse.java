package com.login.main.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OTP 驗證成功回應 DTO
 *
 * 承載 OTP 驗證通過後核發的一次性 Reset Token。
 * 前端應將此 Token 附加至 /reset-password 頁面的 URL query 參數，
 * 並於送出新密碼時一併傳回後端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OTP 驗證成功回應")
public class VerifyOtpResponse {

    @Schema(description = "一次性密碼重設權杖，有效期 10 分鐘")
    private String resetToken;
}