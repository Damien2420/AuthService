package com.login.main.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Email 驗證連結 token 驗證請求 DTO
 *
 * 使用者點擊驗證連結後，前端頁面取出 URL 中的 token 並透過此 DTO 送往後端完成驗證。
 */
@Data
public class VerifyEmailTokenRequest {

    /**
     * 驗證連結中的 UUID token
     */
    @NotBlank(message = "token 不可為空白")
    private String token;
}
