package com.login.main.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * OAuth2 授權碼換取 Token 請求 DTO
 *
 * 承載前端在 OAuth2 Callback 頁面向後端換取 JWT Token 所需的一次性授權碼。
 */
@Data
@Schema(description = "OAuth2 授權碼換取 Token 請求")
public class OAuth2TokenRequest {

    @NotBlank(message = "授權碼不可為空")
    @Size(max = 36, message = "授權碼格式錯誤")
    @Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
        message = "授權碼格式錯誤"
    )
    @Schema(description = "OAuth2 一次性授權碼 (UUID v4 格式，有效期 60 秒)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String code;
}
