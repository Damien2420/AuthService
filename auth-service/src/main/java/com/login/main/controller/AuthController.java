package com.login.main.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.login.main.common.error.AppException;
import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
import com.login.main.dto.internal.TokenInfo;
import com.login.main.dto.request.ForgotPasswordRequest;
import com.login.main.dto.request.GisLoginRequest;
import com.login.main.dto.request.LoginRequest;
import com.login.main.dto.request.OAuth2TokenRequest;
import com.login.main.dto.request.RegisterRequest;
import com.login.main.dto.request.ResendVerificationRequest;
import com.login.main.dto.request.ResetPasswordRequest;
import com.login.main.dto.request.SendVerificationEmailRequest;
import com.login.main.dto.request.VerifyEmailTokenRequest;
import com.login.main.dto.request.VerifyOtpRequest;
import com.login.main.dto.response.CustomApiResponse;
import com.login.main.dto.response.AuthResponse;
import com.login.main.dto.response.VerifyOtpResponse;
import com.login.main.service.AuthService;
import com.login.main.service.EmailVerificationService;
import com.login.main.service.LoginAttemptService;
import com.login.main.service.OAuth2CodeService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.TurnstileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 認證管理控制器
 *
 * 負責處理系統的使用者認證生命週期，包含帳號密碼登入、社交帳號登入、
 * 註冊、Token 刷新與登出。作為認證模組的唯一 API 入口點。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "認證管理", description = "使用者註冊、登入與 Token 管理")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final OAuth2CodeService oauth2CodeService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptService loginAttemptService;
    private final TurnstileService turnstileService;
    private final AppProperties appProperties;

    /**
     * 使用者註冊
     *
     * 接受前端傳入的電子郵件、帳號與密碼，驗證唯一性後建立新使用者實體。
     * 此介面支援冪等性檢查（透過 X-Idempotency-Key 標頭）。
     *
     * @param registerRequest 包含 email, username, password 的註冊請求 DTO
     * @param request         原生 HTTP 請求環境，用於獲取 IP 資訊
     * @return 包含認證資訊（AccessToken, 使用者名稱）的成功或失敗回應
     */
    @Operation(summary = "使用者註冊", description = "註冊新使用者，系統將預設分配 USER 權限", parameters = {
            @Parameter(name = "X-Idempotency-Key", description = "冪等性 Key，確保重複請求不會重複執行", in = ParameterIn.HEADER, required = false)
    })
    @ApiResponse(responseCode = "200", description = "註冊成功", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "使用者名稱已存在、驗證失敗或人機驗證未通過", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/register")
    public ResponseEntity<CustomApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        log.info("註冊請求 - Email: {}, Username: {}, IP: {}", registerRequest.getEmail(), registerRequest.getUsername(), ip);

        // 人機驗證
        turnstileService.verify(registerRequest.getTurnstileToken()).orThrow(AppException::new);

        long startTime = System.currentTimeMillis();
        TokenInfo tokenInfo = authService.register(
                registerRequest.getEmail(),
                registerRequest.getUsername(),
                registerRequest.getPassword(),
                registerRequest.getVerificationCode()
        ).orThrow(AppException::new);
        long duration = System.currentTimeMillis() - startTime;

        log.info("註冊完成 - 使用者: {}, 耗時: {}ms", registerRequest.getUsername(), duration);
        return buildSuccessResponse(tokenInfo, "註冊成功");
    }

    /**
     * 發送 Email 驗證碼
     *
     * 在使用者填入信箱後呼叫，生成 6 位數 OTP 並寄至指定信箱。
     * 若信箱已存在於系統中，提前回傳 EMAIL_ALREADY_EXISTS，避免使用者等到提交表單才發現。
     *
     * @param request 包含 email 的請求 DTO
     * @return 成功時回傳 200 並提示驗證碼已寄出
     */
    @Operation(summary = "發送 Email 驗證碼", description = "產生 OTP 並寄送至指定信箱，供使用者在完成註冊時驗證身分")
    @ApiResponse(responseCode = "200", description = "驗證碼已寄出")
    @ApiResponse(responseCode = "400", description = "Email 已被使用或格式錯誤", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/email/send-verification")
    public ResponseEntity<CustomApiResponse<Void>> sendEmailVerification(
            @Valid @RequestBody SendVerificationEmailRequest request) {
        log.info("Email 驗證碼發送請求 - Email: {}", request.getEmail());

        emailVerificationService.sendVerificationOtpEmail(request.getEmail()).orThrow(AppException::new);

        log.info("Email 驗證碼發送完成 - Email: {}", request.getEmail());
        return ResponseEntity.ok(CustomApiResponse.success(null, "驗證碼已寄出，請檢查您的信箱"));
    }

    /**
     * 帳號密碼登入
     *
     * 驗證使用者憑證。驗證成功後生成 AccessToken 與 RefreshToken。
     * RefreshToken 會以 HttpOnly Cookie 形式存回瀏覽器，而 AccessToken 則由 Response Body 返回。
     *
     * @param loginRequest 包含 username, password 與 rememberMe 選項的登入請求 DTO
     * @param request      原生 HTTP 請求環境
     * @return 包含 AccessToken 資訊的響應物件
     */
    @Operation(summary = "帳號密碼登入")
    @ApiResponse(responseCode = "200", description = "登入成功")
    @ApiResponse(responseCode = "400", description = "人機驗證未通過", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "401", description = "帳號或密碼錯誤", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "429", description = "帳號已暫時鎖定", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<CustomApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        log.info("登入請求 - Username: {}, IP: {}", loginRequest.getUsername(), ip);

        // 人機驗證
        turnstileService.verify(loginRequest.getTurnstileToken()).orThrow(AppException::new);

        // 帳號鎖定檢查
        if (loginAttemptService.isLocked(loginRequest.getUsername())) {
            log.warn("帳號已鎖定，拒絕登入 - Username: {}", loginRequest.getUsername());
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        long startTime = System.currentTimeMillis();
        // 憑證驗證：失敗時記錄失敗次數，成功時清除記錄
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailedAttempt(loginRequest.getUsername());
            throw e;
        }
        loginAttemptService.clearAttempts(loginRequest.getUsername());

        TokenInfo tokenResult = authService.login(loginRequest.getUsername(), loginRequest.isRememberMe())
                .orThrow(AppException::new);
        long duration = System.currentTimeMillis() - startTime;

        log.info("登入成功 - 使用者: {}, 耗時: {}ms", loginRequest.getUsername(), duration);
        return buildSuccessResponse(tokenResult, "登入成功");
    }

    /**
     * 刷新訪問權杖（AccessToken）
     *
     * 當 AccessToken 過期時，利用客戶端 Cookie 中的 RefreshToken 請求獲取新的 Token。
     *
     * @param refreshToken 從 Cookie 中自動注入的刷新權杖
     * @param request      原生 HTTP 請求環境
     * @return 包含新產生的 AccessToken 資訊
     */
    @Operation(summary = "刷新 Token")
    @ApiResponse(responseCode = "200", description = "刷新成功")
    @ApiResponse(responseCode = "401", description = "Token 已失效", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<CustomApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        log.info("Token 刷新請求 - IP: {}", ip);

        long startTime = System.currentTimeMillis();
        TokenInfo tokenInfo = authService.refreshToken(refreshToken).orThrow(AppException::new);
        long duration = System.currentTimeMillis() - startTime;

        log.info("Token 刷新完成 - 使用者: {}, 耗時: {}ms", tokenInfo.username(), duration);
        return buildSuccessResponse(tokenInfo, "刷新成功");
    }

    /**
     * 使用者登出
     *
     * 將 AccessToken 與 RefreshToken 加入黑名單使其立即失效，並清除 HttpOnly Cookie。
     * 採 fail-open 策略：即使 Redis 異常，仍回傳 200 並清除 Cookie，確保使用者能登出。
     *
     * @param authHeader   Authorization Header（格式：Bearer {token}），用於提取 AccessToken
     * @param refreshToken 從 HttpOnly Cookie 自動注入的刷新權杖
     * @return 登出成功的響應，並在 Set-Cookie 清除 refreshToken Cookie
     */
    @Operation(summary = "使用者登出")
    @ApiResponse(responseCode = "200", description = "登出成功")
    @PostMapping("/logout")
    public ResponseEntity<CustomApiResponse<Void>> logout(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        log.info("登出請求 - 有 AccessToken: {}, 有 RefreshToken: {}", accessToken != null, refreshToken != null);
        authService.logout(accessToken, refreshToken);

        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        log.info("登出完成");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(CustomApiResponse.success(null, "登出成功"));
    }

    /**
     * OAuth2 One-time Code Exchange 端點
     *
     * 接受前端 callback 頁面提交的一次性授權碼，驗證並消費後生成 JWT Token。
     *
     * @param tokenRequest 包含一次性授權碼的請求 DTO
     * @return 包含 AccessToken 的物件，RefreshToken 透過 HttpOnly Cookie 設定
     */
    @Operation(summary = "OAuth2 授權碼換取 Token")
    @ApiResponse(responseCode = "200", description = "換取成功")
    @ApiResponse(responseCode = "400", description = "授權碼無效或已過期", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/oauth2/token")
    public ResponseEntity<CustomApiResponse<AuthResponse>> exchangeOAuth2Code(
            @Valid @RequestBody OAuth2TokenRequest tokenRequest) {
        log.info("OAuth2 授權碼換取 Token 請求");

        Result<String> codeResult;
        try {
            codeResult = oauth2CodeService.consumeCode(tokenRequest.getCode());
        } catch (Exception e) {
            log.error("OAuth2 授權碼換取異常（Redis 連線失敗）: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR);
        }

        String username = codeResult.orThrow(AppException::new);
        TokenInfo tokenResult = authService.login(username, false).orThrow(AppException::new);

        log.info("OAuth2 授權碼換取成功 - 使用者: {}", username);
        return buildSuccessResponse(tokenResult, "OAuth2 登入成功");
    }

    /**
     * GIS One Tap 登入
     *
     * 驗證前端透過 Google Identity Services 取得的 ID Token。
     * 驗證成功後，若帳號已存在則直接登入，否則自動建立新帳號，行為與 OAuth2 流程一致。
     *
     * @param gisLoginRequest 包含 Google ID Token 的請求 DTO
     * @param request         原生 HTTP 請求環境
     * @return 包含 AccessToken 的認證回應，RefreshToken 透過 HttpOnly Cookie 設定
     */
    @Operation(summary = "GIS One Tap 登入", description = "驗證 Google Identity Services 回傳的 ID Token，完成登入或自動建立新帳號")
    @ApiResponse(responseCode = "200", description = "登入成功")
    @ApiResponse(responseCode = "401", description = "ID Token 無效或已過期", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/google/login")
    public ResponseEntity<CustomApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GisLoginRequest gisLoginRequest,
            HttpServletRequest request) {
        log.info("GIS One Tap 登入請求 - IP: {}", request.getRemoteAddr());

        TokenInfo tokenResult = authService.loginWithGoogle(gisLoginRequest.getToken())
                .orThrow(AppException::new);

        log.info("GIS 登入完成");
        return buildSuccessResponse(tokenResult, "Google 登入成功");
    }

    /**
     * 重新寄出 Email 驗證連結
     *
     * 針對已註冊但尚未完成 Email 驗證的使用者，寄出含驗證連結的信件。
     * 帳號已驗證或使用者不存在時回傳對應錯誤。
     *
     * @param request 包含 username 的請求 DTO
     * @return 成功時回傳 200 並提示驗證信已寄出
     */
    @Operation(summary = "重新寄出 Email 驗證連結", description = "針對尚未驗證 Email 的使用者，寄送含驗證連結的信件，連結有效期 24 小時")
    @ApiResponse(responseCode = "200", description = "驗證信已寄出")
    @ApiResponse(responseCode = "400", description = "帳號已完成驗證", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "使用者不存在", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/email/resend-verification")
    public ResponseEntity<CustomApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.info("重新寄出 Email 驗證連結請求 - 使用者: {}", request.getUsername());

        emailVerificationService.resendVerificationLink(request.getUsername()).orThrow(AppException::new);

        log.info("Email 驗證連結寄送完成 - 使用者: {}", request.getUsername());
        return ResponseEntity.ok(CustomApiResponse.success(null, "驗證信已寄出，請至信箱查收"));
    }

    /**
     * Email 驗證連結 token 驗證
     *
     * 使用者點擊信件中的連結後，前端取出 URL 中的 token 送往此端點完成帳號 Email 驗證。
     * token 驗證後即刪除，不可重複使用。
     *
     * @param request 包含 token 的請求 DTO
     * @return 成功時回傳 200 確認驗證完成
     */
    @Operation(summary = "驗證 Email 連結 token", description = "使用者點擊驗證連結後，前端將 token 送往此端點完成 Email 驗證")
    @ApiResponse(responseCode = "200", description = "Email 驗證成功")
    @ApiResponse(responseCode = "400", description = "連結已失效或過期", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/email/verify")
    public ResponseEntity<CustomApiResponse<Void>> verifyEmailToken(
            @Valid @RequestBody VerifyEmailTokenRequest request) {
        log.info("Email 驗證連結 token 驗證請求");

        emailVerificationService.verifyByToken(request.getToken()).orThrow(AppException::new);

        log.info("Email 驗證完成");
        return ResponseEntity.ok(CustomApiResponse.success(null, "Email 驗證成功"));
    }

    /**
     * 忘記密碼 - 發送 OTP 驗證碼
     *
     * 驗證使用者 Email 存在後，生成 6 位數 OTP 並寄送至指定信箱。
     * OTP 有效期為 5 分鐘，且不可重複使用。
     *
     * @param request 包含 email 的忘記密碼請求 DTO
     * @return 成功時回傳 200 並提示驗證碼已寄出
     */
    @Operation(summary = "忘記密碼 - 發送 OTP", description = "驗證帳號存在後，發送 6 位數 OTP 驗證碼至使用者信箱。帳號不存在時仍回 200，防止 email 枚舉攻擊。")
    @ApiResponse(responseCode = "200", description = "驗證碼已寄出（帳號不存在時行為相同）")
    @PostMapping("/password/forgot")
    public ResponseEntity<CustomApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        log.info("忘記密碼請求 - Email: {}", request.getEmail());

        passwordResetService.sendOtp(request.getEmail()).orThrow(AppException::new);

        log.info("OTP 發送完成 - Email: {}", request.getEmail());
        return ResponseEntity.ok(CustomApiResponse.success(null, "驗證碼已寄出，請檢查您的信箱"));
    }

    /**
     * 忘記密碼 - 驗證 OTP
     *
     * 驗證使用者輸入的 6 位數 OTP，成功後核發一次性 Reset Token（有效期 10 分鐘）。
     * OTP 在驗證後即被消費，無法重複使用。
     *
     * @param request 包含 email 與 otp 的驗證請求 DTO
     * @return 成功時回傳含 Reset Token 的回應
     */
    @Operation(summary = "驗證 OTP", description = "驗證 6 位數 OTP，成功後核發一次性 Reset Token")
    @ApiResponse(responseCode = "200", description = "驗證成功，回傳 Reset Token")
    @ApiResponse(responseCode = "400", description = "驗證碼無效或已過期", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/password/verify-otp")
    public ResponseEntity<CustomApiResponse<VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        log.info("OTP 驗證請求 - Email: {}", request.getEmail());

        String resetToken = passwordResetService.verifyOtp(request.getEmail(), request.getOtp())
                .orThrow(AppException::new);

        log.info("OTP 驗證成功 - Email: {}", request.getEmail());
        return ResponseEntity.ok(CustomApiResponse.success(new VerifyOtpResponse(resetToken), "驗證成功"));
    }

    /**
     * 忘記密碼 - 重設密碼
     *
     * 驗證 Reset Token 後更新使用者密碼，並在 Redis 記錄重設時間戳，
     * 使該使用者所有在重設前發出的 JWT Token 立即失效。
     *
     * @param request 包含 resetToken 與 newPassword 的重設請求 DTO
     * @return 成功時回傳 200
     */
    @Operation(summary = "重設密碼", description = "使用 Reset Token 完成密碼重設，並使所有現有 Session 失效")
    @ApiResponse(responseCode = "200", description = "密碼重設成功")
    @ApiResponse(responseCode = "400", description = "重設權杖無效或已過期", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PostMapping("/password/reset")
    public ResponseEntity<CustomApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        log.info("密碼重設請求");

        passwordResetService.resetPassword(request.getResetToken(), request.getNewPassword())
                .orThrow(AppException::new);

        log.info("密碼重設完成");
        return ResponseEntity.ok(CustomApiResponse.success(null, "密碼已重設，請使用新密碼重新登入"));
    }

    /**
     * 建立認證成功的 HTTP 回應
     *
     * 將 TokenInfo 包裝為 AuthResponse，並將 RefreshToken 設定於 HttpOnly Cookie。
     *
     * @param tokenResult 包含 AccessToken、RefreshToken 與使用者名稱的 Token 資訊
     * @param message     回應訊息
     * @return 帶有 Set-Cookie Header 的 ResponseEntity
     */
    private ResponseEntity<CustomApiResponse<AuthResponse>> buildSuccessResponse(TokenInfo tokenResult, String message) {
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(tokenResult.accessToken())
                .username(tokenResult.username())
                .build();

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenResult.refreshToken())
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .path("/")
                .maxAge(tokenResult.refreshTokenMaxAge())
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(CustomApiResponse.success(authResponse, message));
    }
}
