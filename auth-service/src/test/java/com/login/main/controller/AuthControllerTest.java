package com.login.main.controller;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
import com.login.main.dto.internal.TokenInfo;
import com.login.main.filter.IdempotencyFilter;
import com.login.main.filter.RateLimitFilter;
import com.login.main.service.AuthService;
import com.login.main.service.EmailVerificationService;
import com.login.main.service.LoginAttemptService;
import com.login.main.service.OAuth2CodeService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.TurnstileService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 的 Web 層切片測試
 *
 * 使用 @WebMvcTest 只啟動 Web 層，關閉所有 Security Filter，
 * 驗證 HTTP contract、Bean Validation、cookie 屬性與 ErrorCode 到 HTTP status 的映射。
 * Filter / Security 行為由 Layer 3 另行覆蓋。
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {IdempotencyFilter.class, RateLimitFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private OAuth2CodeService oauth2CodeService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private LoginAttemptService loginAttemptService;

    @MockitoBean
    private TurnstileService turnstileService;

    @MockitoBean
    private AppProperties appProperties;

    /** 各測試共用的 stub TokenInfo */
    private static final TokenInfo STUB_TOKEN = new TokenInfo("access-token", "refresh-token", "testuser", 86400);

    /** UUID v4 格式的合法授權碼，供 OAuth2 端點測試使用 */
    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        // cookie.secure 預設 false，所有測試共用此設定
        AppProperties.Cookie cookie = new AppProperties.Cookie();
        when(appProperties.getCookie()).thenReturn(cookie);

        // Turnstile 預設放行，只有明確測試驗證失敗的 case 才覆寫
        when(turnstileService.verify(any())).thenReturn(Result.success());
    }

    // =========================================================
    //  POST /api/v1/auth/register
    // =========================================================

    @Nested
    class Register {

        private static final String VALID_BODY = """
                {
                    "email": "user@example.com",
                    "username": "testuser",
                    "password": "pass123",
                    "verificationCode": "123456",
                    "turnstileToken": "mock-token"
                }
                """;

        @Test
        void whenSuccess_shouldReturn200WithAccessToken() throws Exception {
            when(authService.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(cookie().exists("refreshToken"));

            verify(turnstileService).verify("mock-token");
        }

        @Test
        void whenSuccess_refreshTokenCookie_shouldHaveHttpOnlyPathAndSameSite() throws Exception {
            when(authService.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                    .andExpect(header().string("Set-Cookie", containsString("Max-Age=86400")));
        }

        @Test
        void whenTurnstileFails_shouldReturn400() throws Exception {
            when(turnstileService.verify(any())).thenReturn(Result.fail(ErrorCode.CAPTCHA_FAILED));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("CAPTCHA_FAILED"));
        }

        @Test
        void whenTurnstileTokenMissing_shouldReturn400ViaTurnstileService() throws Exception {
            String body = """
                    {
                        "email": "user@example.com",
                        "username": "testuser",
                        "password": "pass123",
                        "verificationCode": "123456"
                    }
                    """;
            when(turnstileService.verify(isNull())).thenReturn(Result.fail(ErrorCode.CAPTCHA_FAILED));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("CAPTCHA_FAILED"));

            verifyNoInteractions(authService);
        }

        @Test
        void whenEmailOtpFails_shouldReturn400() throws Exception {
            when(authService.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Result.fail(ErrorCode.EMAIL_VERIFICATION_OTP_INVALID));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("EMAIL_VERIFICATION_OTP_INVALID"));
        }

        @Test
        void whenUsernameDuplicated_shouldReturn400() throws Exception {
            when(authService.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Result.fail(ErrorCode.USER_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("USER_ALREADY_EXISTS"));
        }

        @Test
        void whenEmailDuplicated_shouldReturn400() throws Exception {
            when(authService.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Result.fail(ErrorCode.EMAIL_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        void whenRequiredFieldsMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // =========================================================
    //  POST /api/v1/auth/login
    // =========================================================

    @Nested
    class Login {

        private static final String VALID_BODY = """
                {"username": "testuser", "password": "pass123", "turnstileToken": "mock-token"}
                """;

        @Test
        void whenSuccess_shouldReturn200WithAccessToken() throws Exception {
            when(loginAttemptService.isLocked(anyString())).thenReturn(false);
            when(authService.login(anyString(), anyBoolean())).thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(cookie().exists("refreshToken"));

            verify(turnstileService).verify("mock-token");
        }

        @Test
        void whenSuccess_shouldCallClearAttempts() throws Exception {
            when(loginAttemptService.isLocked(anyString())).thenReturn(false);
            when(authService.login(anyString(), anyBoolean())).thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk());

            verify(loginAttemptService).clearAttempts("testuser");
        }

        @Test
        void whenBadCredentials_shouldReturn401AndRecordFailedAttempt() throws Exception {
            when(loginAttemptService.isLocked(anyString())).thenReturn(false);
            doThrow(new BadCredentialsException("bad credentials"))
                    .when(authenticationManager).authenticate(any());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());

            verify(loginAttemptService).recordFailedAttempt("testuser");
        }

        @Test
        void whenEmailNotVerified_shouldReturn403() throws Exception {
            when(loginAttemptService.isLocked(anyString())).thenReturn(false);
            doThrow(new DisabledException("disabled"))
                    .when(authenticationManager).authenticate(any());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorKey").value("EMAIL_NOT_VERIFIED"));
        }

        @Test
        void whenAccountLocked_shouldReturn429WithoutCallingAuthenticationManager() throws Exception {
            when(loginAttemptService.isLocked(anyString())).thenReturn(true);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.errorKey").value("ACCOUNT_LOCKED"));

            verifyNoInteractions(authenticationManager);
        }

        @Test
        void whenTurnstileFails_shouldReturn400WithoutCallingAuthenticationManager() throws Exception {
            when(turnstileService.verify(any())).thenReturn(Result.fail(ErrorCode.CAPTCHA_FAILED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationManager);
        }

        @Test
        void whenTurnstileTokenMissing_shouldReturn400WithoutCallingAuthenticationManager() throws Exception {
            String body = "{\"username\": \"testuser\", \"password\": \"pass123\"}";
            when(turnstileService.verify(isNull())).thenReturn(Result.fail(ErrorCode.CAPTCHA_FAILED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("CAPTCHA_FAILED"));

            verifyNoInteractions(authenticationManager);
        }

        @Test
        void whenRequiredFieldsMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // =========================================================
    //  POST /api/v1/auth/refresh
    // =========================================================

    @Nested
    class Refresh {

        @Test
        void whenValidToken_shouldReturn200WithNewTokenAndCookie() throws Exception {
            when(authService.refreshToken("valid-token")).thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "valid-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(cookie().exists("refreshToken"))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                    .andExpect(header().string("Set-Cookie", containsString("Max-Age=86400")));
        }

        @Test
        void whenCookieMissing_shouldReturn401() throws Exception {
            when(authService.refreshToken(null)).thenReturn(Result.fail(ErrorCode.TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorKey").value("TOKEN_INVALID"));
        }

        @Test
        void whenCookieEmpty_shouldReturn401() throws Exception {
            when(authService.refreshToken("")).thenReturn(Result.fail(ErrorCode.TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorKey").value("TOKEN_INVALID"));
        }

        @Test
        void whenTokenRevoked_shouldReturn401() throws Exception {
            when(authService.refreshToken("revoked")).thenReturn(Result.fail(ErrorCode.TOKEN_REVOKED));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "revoked")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorKey").value("TOKEN_REVOKED"));
        }

        @Test
        void whenTokenInvalid_shouldReturn401() throws Exception {
            when(authService.refreshToken("bad")).thenReturn(Result.fail(ErrorCode.TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "bad")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenServiceReturnsInternalError_shouldReturn500() throws Exception {
            when(authService.refreshToken(anyString())).thenReturn(Result.fail(ErrorCode.INTERNAL_ERROR));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "any")))
                    .andExpect(status().isInternalServerError());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/logout
    // =========================================================

    @Nested
    class Logout {

        @Test
        void whenBothTokensPresent_shouldReturn200AndCallLogout() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer access-token")
                            .cookie(new Cookie("refreshToken", "refresh-token")))
                    .andExpect(status().isOk());

            verify(authService).logout("access-token", "refresh-token");
        }

        @Test
        void whenAuthorizationNotBearer_shouldPassNullAccessToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Token something")
                            .cookie(new Cookie("refreshToken", "refresh-token")))
                    .andExpect(status().isOk());

            verify(authService).logout(null, "refresh-token");
        }

        @Test
        void whenNoTokens_failOpen_shouldReturn200() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk());

            verify(authService).logout(null, null);
        }

        @Test
        void shouldClearRefreshTokenCookieWithMaxAge0AndHttpOnlyAndSameSite() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
        }
    }

    // =========================================================
    //  POST /api/v1/auth/oauth2/token
    // =========================================================

    @Nested
    class OAuth2Token {

        @Test
        void whenValidCode_shouldReturn200WithToken() throws Exception {
            when(oauth2CodeService.consumeCode(VALID_UUID)).thenReturn(Result.success("testuser"));
            when(authService.login("testuser", false)).thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/oauth2/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"" + VALID_UUID + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(cookie().exists("refreshToken"));
        }

        @Test
        void whenInvalidCode_shouldReturn400() throws Exception {
            when(oauth2CodeService.consumeCode(VALID_UUID))
                    .thenReturn(Result.fail(ErrorCode.OAUTH2_CODE_INVALID));

            mockMvc.perform(post("/api/v1/auth/oauth2/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"" + VALID_UUID + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("OAUTH2_CODE_INVALID"));
        }

        @Test
        void whenRedisThrows_shouldReturn500() throws Exception {
            when(oauth2CodeService.consumeCode(anyString()))
                    .thenThrow(new RuntimeException("Redis connection failed"));

            mockMvc.perform(post("/api/v1/auth/oauth2/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"" + VALID_UUID + "\"}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        void whenCodeValidButLoginFails_shouldReturnCorrespondingStatus() throws Exception {
            when(oauth2CodeService.consumeCode(VALID_UUID)).thenReturn(Result.success("testuser"));
            when(authService.login("testuser", false)).thenReturn(Result.fail(ErrorCode.INTERNAL_ERROR));

            mockMvc.perform(post("/api/v1/auth/oauth2/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"" + VALID_UUID + "\"}"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        void whenCodeValidButLoginReturnsTokenInvalid_shouldReturn401() throws Exception {
            when(oauth2CodeService.consumeCode(VALID_UUID)).thenReturn(Result.success("testuser"));
            when(authService.login("testuser", false)).thenReturn(Result.fail(ErrorCode.TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/oauth2/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"" + VALID_UUID + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorKey").value("TOKEN_INVALID"));
        }
    }

    // =========================================================
    //  POST /api/v1/auth/google/login
    // =========================================================

    @Nested
    class GoogleLogin {

        @Test
        void whenValidIdToken_shouldReturn200WithToken() throws Exception {
            when(authService.loginWithGoogle("valid-id-token")).thenReturn(Result.success(STUB_TOKEN));

            mockMvc.perform(post("/api/v1/auth/google/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\": \"valid-id-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(cookie().exists("refreshToken"));
        }

        @Test
        void whenInvalidIdToken_shouldReturn401() throws Exception {
            when(authService.loginWithGoogle(anyString()))
                    .thenReturn(Result.fail(ErrorCode.TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/google/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\": \"invalid-id-token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorKey").value("TOKEN_INVALID"));
        }

        @Test
        void whenTokenFieldMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/google/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/email/send-verification
    // =========================================================

    @Nested
    class SendEmailVerification {

        @Test
        void whenNewEmail_shouldReturn200() throws Exception {
            when(emailVerificationService.sendVerificationOtpEmail(anyString()))
                    .thenReturn(Result.success());

            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"new@example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void whenEmailAlreadyExists_shouldReturn400() throws Exception {
            when(emailVerificationService.sendVerificationOtpEmail(anyString()))
                    .thenReturn(Result.fail(ErrorCode.EMAIL_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"taken@example.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        void whenEmailFormatInvalid_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"not-an-email\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void whenEmailMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/email/verify
    // =========================================================

    @Nested
    class VerifyEmailToken {

        @Test
        void whenValidToken_shouldReturn200() throws Exception {
            when(emailVerificationService.verifyByToken(anyString())).thenReturn(Result.success());

            mockMvc.perform(post("/api/v1/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\": \"valid-uuid-token\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void whenTokenExpiredOrMissing_shouldReturn400() throws Exception {
            when(emailVerificationService.verifyByToken(anyString()))
                    .thenReturn(Result.fail(ErrorCode.VERIFICATION_LINK_INVALID));

            mockMvc.perform(post("/api/v1/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\": \"expired-token\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("VERIFICATION_LINK_INVALID"));
        }

        @Test
        void whenTokenMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/email/resend-verification
    // =========================================================

    @Nested
    class ResendVerification {

        @Test
        void whenUserNotVerified_shouldReturn200() throws Exception {
            when(emailVerificationService.resendVerificationLink(anyString()))
                    .thenReturn(Result.success());

            mockMvc.perform(post("/api/v1/auth/email/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"testuser\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void whenUserAlreadyVerified_shouldReturn400() throws Exception {
            when(emailVerificationService.resendVerificationLink(anyString()))
                    .thenReturn(Result.fail(ErrorCode.EMAIL_ALREADY_VERIFIED));

            mockMvc.perform(post("/api/v1/auth/email/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"verified\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("EMAIL_ALREADY_VERIFIED"));
        }

        @Test
        void whenUserNotFound_shouldReturn404() throws Exception {
            when(emailVerificationService.resendVerificationLink(anyString()))
                    .thenReturn(Result.fail(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(post("/api/v1/auth/email/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"nobody\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenRateLimitExceeded_shouldReturn429() throws Exception {
            when(emailVerificationService.resendVerificationLink(anyString()))
                    .thenReturn(Result.fail(ErrorCode.RATE_LIMIT_EXCEEDED));

            mockMvc.perform(post("/api/v1/auth/email/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"testuser\"}"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/password/forgot
    // =========================================================

    @Nested
    class ForgotPassword {

        @Test
        void whenEmailExists_shouldReturn200() throws Exception {
            when(passwordResetService.sendOtp(anyString())).thenReturn(Result.success());

            mockMvc.perform(post("/api/v1/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"user@example.com\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void whenEmailNotExists_antiEnumeration_shouldReturn200() throws Exception {
            // sendOtp 對不存在的 email 回傳 success，避免洩漏帳號是否存在
            when(passwordResetService.sendOtp(anyString())).thenReturn(Result.success());

            mockMvc.perform(post("/api/v1/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"nobody@example.com\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void whenEmailFormatInvalid_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"not-an-email\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void whenEmailMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/password/verify-otp
    // =========================================================

    @Nested
    class VerifyOtp {

        @Test
        void whenOtpCorrect_shouldReturn200WithResetToken() throws Exception {
            when(passwordResetService.verifyOtp("user@example.com", "123456"))
                    .thenReturn(Result.success("reset-token-uuid"));

            mockMvc.perform(post("/api/v1/auth/password/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"user@example.com\", \"otp\": \"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.resetToken").value("reset-token-uuid"));
        }

        @Test
        void whenOtpInvalidOrExpired_shouldReturn400() throws Exception {
            when(passwordResetService.verifyOtp(anyString(), anyString()))
                    .thenReturn(Result.fail(ErrorCode.OTP_INVALID));

            mockMvc.perform(post("/api/v1/auth/password/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"user@example.com\", \"otp\": \"000000\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("OTP_INVALID"));
        }

        @Test
        void whenOtpTooShort_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"user@example.com\", \"otp\": \"12345\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void whenRequiredFieldsMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================
    //  POST /api/v1/auth/password/reset
    // =========================================================

    @Nested
    class ResetPassword {

        @Test
        void whenResetTokenValid_shouldReturn200() throws Exception {
            when(passwordResetService.resetPassword(anyString(), anyString()))
                    .thenReturn(Result.success());

            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resetToken\": \"valid-token\", \"newPassword\": \"newPass123\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void whenResetTokenExpiredOrMissing_shouldReturn400() throws Exception {
            when(passwordResetService.resetPassword(anyString(), anyString()))
                    .thenReturn(Result.fail(ErrorCode.RESET_TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resetToken\": \"expired-token\", \"newPassword\": \"newPass123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("RESET_TOKEN_INVALID"));
        }

        @Test
        void whenRequiredFieldsMissing_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void whenNewPasswordTooShort_beanValidation_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resetToken\": \"some-token\", \"newPassword\": \"abc\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
