package com.login.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.login.main.filter.JwtAuthenticationFilter;
import com.login.main.handler.JwtErrorHandlerEntry;
import com.login.main.handler.OAuth2AuthenticationHandler;
import com.login.main.service.CustomUserDetailsService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.TokenBlacklistService;
import com.login.main.util.JwtUtil;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 安全性過濾鏈配置
 * 
 * 負責定義系統的存取授權規則、CORS 策略、Filter 執行順序以及 OAuth2 登入整合。
 * 採用無狀態 (Stateless) 管理策略，適合 JWT 架構。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtErrorHandlerEntry jwtErrorHandlerEntry;
    private final OAuth2AuthenticationHandler oAuth2AuthenticationHandler;
    private final AppProperties appProperties;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordResetService passwordResetService;

    /**
     * SecurityConfig 建構子
     *
     * @param jwtErrorHandlerEntry JWT 異常處理進入點
     * @param oAuth2AuthenticationHandler OAuth2 登入成功處理器
     * @param appProperties 應用程式內容參數
     * @param jwtUtil JWT 工具類
     * @param customUserDetailsService 使用者詳細資訊服務
     * @param tokenBlacklistService Token 黑名單服務
     * @param passwordResetService 密碼重設服務
     */
    public SecurityConfig(
            JwtErrorHandlerEntry jwtErrorHandlerEntry,
            OAuth2AuthenticationHandler oAuth2AuthenticationHandler,
            AppProperties appProperties,
            JwtUtil jwtUtil,
            CustomUserDetailsService customUserDetailsService,
            TokenBlacklistService tokenBlacklistService,
            PasswordResetService passwordResetService) {
        this.jwtErrorHandlerEntry = jwtErrorHandlerEntry;
        this.oAuth2AuthenticationHandler = oAuth2AuthenticationHandler;
        this.appProperties = appProperties;
        this.jwtUtil = jwtUtil;
        this.customUserDetailsService = customUserDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * 建立 JwtAuthenticationFilter Bean
     *
     * @return JwtAuthenticationFilter 實例
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(
                jwtUtil,
                customUserDetailsService,
                tokenBlacklistService,
                passwordResetService,
                jwtErrorHandlerEntry);
    }

    /**
     * 配置 HTTP 安全性過濾器鏈
     * 
     * 定義認證放行路徑、Session 策略、異常處理器與自定義 Filter 順序。
     * @param http HttpSecurity 配置物件
     * @return 配置後的 SecurityFilterChain
     * @throws Exception 配置過程出錯時拋出異常
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Stateless API 不需要 CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/monitoring/tunnel").permitAll()  // Sentry Tunnel
                        .requestMatchers("/login/**", "/oauth2/**", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtErrorHandlerEntry))
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2AuthenticationHandler));

        return http.build();
    }

    /**
     * LINE OIDC ID Token 解碼器工廠
     *
     * LINE 使用 HS256（對稱加密）簽署 ID Token，而 Spring Security 預設期望 RS256（非對稱）。
     * 此 Bean 針對 LINE provider 建立使用 Channel Secret 的 HMAC-SHA256 解碼器，其餘 provider 維持預設行為。
     * @return 依 provider 路由解碼策略的 JwtDecoderFactory
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory defaultFactory = new OidcIdTokenDecoderFactory();
        return clientRegistration -> {
            if ("line".equals(clientRegistration.getRegistrationId())) {
                SecretKeySpec secretKey = new SecretKeySpec(
                    clientRegistration.getClientSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
                );
                return NimbusJwtDecoder.withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();
            }
            return defaultFactory.createDecoder(clientRegistration);
        };
    }

    /**
     * 配置 CORS 跨域策略
     * 
     * 限制允許存取的來源、方法與標頭，確保前端 SPA 應用程式能安全調用 API。
     * @return CorsConfigurationSource 實體
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(appProperties.getFrontendUrl())); // 允許 Frontend URL
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
