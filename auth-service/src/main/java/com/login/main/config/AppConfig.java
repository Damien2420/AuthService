package com.login.main.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 核心應用程式配置類別
 * 
 * 提供系統級別的 Bean 定義，包含 JSON 序列化器 (ObjectMapper)、密碼加密器 (PasswordEncoder)
 * 以及安全性認證管理器 (AuthenticationManager)。同時啟動 JPA 審計功能。
 */
@Configuration
@EnableJpaAuditing
public class AppConfig {

    /**
     * 配置 Jackson ObjectMapper
     * 
     * 自定義 JSON 序列化行為，包含支援 Java 8 時間模組與格式化日期輸出。
     * @return 經配置後的 ObjectMapper 實體
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * 配置密碼加密器
     * 
     * 提供 BCrypt 強度的雜湊演算法用於使用者密碼加密。
     * @return BCryptPasswordEncoder 實體
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置認證管理器
     *
     * 從 Spring Security 配置中獲取標準的身份驗證管理器。
     * @param authConfig 認證配置物件
     * @return AuthenticationManager 實體
     * @throws Exception 獲取失敗時拋出異常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * 配置 RestTemplate
     *
     * @return RestTemplate 實體
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    /**
     * 配置 RestClient
     *
     * @return RestClient 實體
     */
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
