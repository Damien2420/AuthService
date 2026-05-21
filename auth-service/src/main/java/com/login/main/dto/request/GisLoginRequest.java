package com.login.main.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Google One Tap 登入請求 DTO
 *
 * 承載前端透過 Google Identity Services 取得的 ID Token，
 * 供後端使用 GoogleIdTokenVerifier 驗證真偽後完成登入。
 */
@Data
@Schema(description = "GIS One Tap 登入請求")
public class GisLoginRequest {

    @NotBlank(message = "Google ID Token 不可為空")
    @Schema(description = "Google Identity Services 回傳的 ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
    private String token;
}