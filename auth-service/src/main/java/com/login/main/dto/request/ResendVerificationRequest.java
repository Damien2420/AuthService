package com.login.main.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重新寄出 Email 驗證連結請求 DTO
 *
 * 用於登入時帳號尚未驗證的使用者，透過此請求重新寄送含驗證連結的信件。
 */
@Data
public class ResendVerificationRequest {

    /**
     * 使用者帳號名稱
     */
    @NotBlank(message = "使用者名稱不可為空白")
    private String username;
}
