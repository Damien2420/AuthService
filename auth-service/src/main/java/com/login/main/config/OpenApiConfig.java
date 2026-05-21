package com.login.main.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 接口文件配置
 * 
 * 負責定義 API 文件的基本資訊、聯絡人、許可證，並配置基於 Bearer JWT 的安全性認證機制。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 核心定義
     * 
     * 整合 API 基本資訊與全域安全性需求。
     * @return OpenAPI 實體
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme()));
    }

    /**
     * 定義 API 基本資訊
     * 
     * 設置 API 的標題、描述、版本與聯絡資訊。
     * @return Info 物件
     */
    private Info apiInfo() {
        return new Info()
                .title("JWT 登入範例 API")
                .description("這是一個使用 Spring Boot 和 JWT 實現登入功能的範例 API。")
                .version("1.0.0")
                .contact(new Contact()
                        .name("你的名字")
                        .url("http://your-website.com")
                        .email("your.email@example.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("http://www.apache.org/licenses/LICENSE-2.0.html"))
                .termsOfService("http://example.com/terms");
    }

    /**
     * 定義安全性方案
     * 
     * 提供 Swagger UI 介面輸入 JWT 代碼的 Bearer 認證配置。
     * @return SecurityScheme 物件
     */
    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("請輸入 JWT Token (格式: Bearer <token>)");
    }
}
