package com.login.main.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "認證結果回應")
public class AuthResponse {
    @Schema(description = "Access Token")
    private String accessToken;

    @Schema(description = "使用者名稱")
    private String username;
}
