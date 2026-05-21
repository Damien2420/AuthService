package com.login.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.login.main.dto.request.LoginRequest;
import com.login.main.dto.request.RegisterRequest;
import com.login.main.dto.request.ResetPasswordRequest;
import com.login.main.dto.request.VerifyEmailTokenRequest;
import com.login.main.dto.request.VerifyOtpRequest;
import com.login.main.dto.response.AuthResponse;
import com.login.main.dto.response.CustomApiResponse;
import com.login.main.dto.response.VerifyOtpResponse;
import org.springframework.core.ParameterizedTypeReference;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("integration-test")
public class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean JavaMailSender mailSender;

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    // EmailVerificationServiceImpl.verifyByToken 使用 StringRedisTemplate 讀取驗證連結 token，
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM user_roles");
        jdbcTemplate.execute("DELETE FROM social_accounts");
        jdbcTemplate.execute("DELETE FROM users");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // --- 共用輔助方法 ---

    /**
     * 透過 register API 建立已驗證使用者並回傳 login 回應（含 refreshToken Cookie）
     */
    private ResponseEntity<CustomApiResponse<AuthResponse>> registerAndLogin(
            String email, String username, String password, String otpKey) {
        redisTemplate.opsForValue().set(otpKey, "100001");
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(email);
        reg.setUsername(username);
        reg.setPassword(password);
        reg.setVerificationCode("100001");
        restTemplate.exchange("/api/v1/auth/register", HttpMethod.POST,
                new HttpEntity<>(reg),
                new ParameterizedTypeReference<CustomApiResponse<AuthResponse>>() {});

        LoginRequest login = new LoginRequest();
        login.setUsername(username);
        login.setPassword(password);
        return restTemplate.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<CustomApiResponse<AuthResponse>>() {});
    }

    /**
     * 從 Set-Cookie 回應標頭提取 refreshToken 的值
     */
    private String extractRefreshToken(ResponseEntity<?> resp) {
        String setCookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().contains("refreshToken=");
        return setCookie.split(";")[0].split("=", 2)[1];
    }

    /**
     * 直接在 DB 建立已驗證使用者，繞過 HTTP 流程，不消耗任何 rate limit token。
     * 適用於需要真實使用者但不測試 register/login 流程本身的測試。
     */
    private void createVerifiedUser(String email, String username, String password) {
        String userId = UUID.randomUUID().toString();
        // 確保 ROLE_USER 存在（跨測試共用，@BeforeEach 不清 roles 表）
        jdbcTemplate.update(
                "INSERT INTO roles (name) VALUES (?) ON CONFLICT (name) DO NOTHING", "ROLE_USER");
        jdbcTemplate.update(
                "INSERT INTO users (id, email, username, nickname, password, is_verified, is_blacklisted, created_at, updated_at) "
                + "VALUES (?::uuid, ?, ?, ?, ?, true, false, NOW(), NOW())",
                userId, email, username, username, passwordEncoder.encode(password));
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT ?::uuid, id FROM roles WHERE name = ?",
                userId, "ROLE_USER");
    }

    // ---

    @Test
    void register_happyPath_shouldReturn200WithToken() {
        // 預塞 OTP 進 Redis
        redisTemplate.opsForValue().set("email:verify:otp:test@example.com", "123456");

        // 建構請求
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setUsername("testuser");
        req.setPassword("password123");
        req.setVerificationCode("123456");

        // 呼叫 API — 使用 exchange + ParameterizedTypeReference 處理泛型包裝層
        ResponseEntity<CustomApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<CustomApiResponse<AuthResponse>>() {});

        // 斷言
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getData()).isNotNull();
        assertThat(resp.getBody().getData().getAccessToken()).isNotBlank();
        assertThat(resp.getBody().getData().getUsername()).isEqualTo("testuser");
    }

    @Test
    void login_happyPath_shouldReturn200WithTokenAndCookie() {
        ResponseEntity<CustomApiResponse<AuthResponse>> resp = registerAndLogin(
                "login@example.com", "loginuser", "Password123",
                "email:verify:otp:login@example.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getData().getAccessToken()).isNotBlank();
        // refreshToken 以 HttpOnly Cookie 形式回傳
        assertThat(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("refreshToken=");
    }

    @Test
    void login_fiveConsecutiveFailures_shouldReturn429OnNextAttempt() {
        // 直接建立使用者，不透過 HTTP，確保 6 次 login 全部屬於本測試，rate limit 不會干擾帳號鎖定驗證
        createVerifiedUser("lockout@example.com", "lockoutuser", "Password123");

        // 連續 5 次密碼錯誤 — 每次應回 401
        LoginRequest badLogin = new LoginRequest();
        badLogin.setUsername("lockoutuser");
        badLogin.setPassword("wrong-password");
        for (int i = 0; i < 5; i++) {
            ResponseEntity<CustomApiResponse<Void>> failResp = restTemplate.exchange(
                    "/api/v1/auth/login", HttpMethod.POST,
                    new HttpEntity<>(badLogin),
                    new ParameterizedTypeReference<CustomApiResponse<Void>>() {});
            assertThat(failResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 第 6 次應觸發帳號鎖定，回傳 429
        ResponseEntity<CustomApiResponse<Void>> lockedResp = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(badLogin),
                new ParameterizedTypeReference<CustomApiResponse<Void>>() {});

        assertThat(lockedResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void refresh_withValidToken_shouldRotateAndBlacklistOldToken() {
        // 建立使用者並取得含 refreshToken 的登入回應
        ResponseEntity<CustomApiResponse<AuthResponse>> loginResp = registerAndLogin(
                "refresh@example.com", "refreshuser", "Password123",
                "email:verify:otp:refresh@example.com");
        String oldRefreshToken = extractRefreshToken(loginResp);

        // 第一次 refresh — 應成功並回傳新 accessToken
        HttpHeaders cookieHeaders = new HttpHeaders();
        cookieHeaders.add("Cookie", "refreshToken=" + oldRefreshToken);
        ResponseEntity<CustomApiResponse<AuthResponse>> firstRefresh = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(cookieHeaders),
                new ParameterizedTypeReference<CustomApiResponse<AuthResponse>>() {});

        assertThat(firstRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstRefresh.getBody().getData().getAccessToken()).isNotBlank();

        // 舊 refreshToken 已被加入黑名單，再次使用應回傳 401
        ResponseEntity<CustomApiResponse<Void>> secondRefresh = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(cookieHeaders),
                new ParameterizedTypeReference<CustomApiResponse<Void>>() {});

        assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordReset_shouldInvalidateRefreshTokenIssuedBefore() {
        // 建立使用者並取得登入 refreshToken
        ResponseEntity<CustomApiResponse<AuthResponse>> loginResp = registerAndLogin(
                "pwreset@example.com", "pwresetuser", "Password123",
                "email:verify:otp:pwreset@example.com");
        String oldRefreshToken = extractRefreshToken(loginResp);

        // 預置密碼重設 OTP
        redisTemplate.opsForValue().set("pwd_reset:otp:pwreset@example.com", "654321");

        // 驗證 OTP → 取得一次性 resetToken
        VerifyOtpRequest otpReq = new VerifyOtpRequest();
        otpReq.setEmail("pwreset@example.com");
        otpReq.setOtp("654321");
        ResponseEntity<CustomApiResponse<VerifyOtpResponse>> otpResp = restTemplate.exchange(
                "/api/v1/auth/password/verify-otp", HttpMethod.POST,
                new HttpEntity<>(otpReq),
                new ParameterizedTypeReference<CustomApiResponse<VerifyOtpResponse>>() {});
        assertThat(otpResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String resetToken = otpResp.getBody().getData().getResetToken();

        // 執行密碼重設 — 系統記錄重設時間戳，使重設前發出的 Token 失效
        ResetPasswordRequest resetReq = new ResetPasswordRequest();
        resetReq.setResetToken(resetToken);
        resetReq.setNewPassword("NewPass456");
        restTemplate.exchange("/api/v1/auth/password/reset", HttpMethod.POST,
                new HttpEntity<>(resetReq),
                new ParameterizedTypeReference<CustomApiResponse<Void>>() {});

        // 嘗試以重設前取得的舊 refreshToken 換取新 Token — 應回傳 401
        HttpHeaders cookieHeaders = new HttpHeaders();
        cookieHeaders.add("Cookie", "refreshToken=" + oldRefreshToken);
        ResponseEntity<CustomApiResponse<Void>> refreshResp = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(cookieHeaders),
                new ParameterizedTypeReference<CustomApiResponse<Void>>() {});

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emailVerificationLink_shouldSetUserVerifiedAndConsumeToken() {
        // 直接在 DB 建立未驗證使用者（模擬尚未完成 Email 驗證的帳號）
        String userId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, username, nickname, is_verified, is_blacklisted, created_at, updated_at) "
                + "VALUES (?::uuid, ?, ?, ?, false, false, NOW(), NOW())",
                userId, "unverified@example.com", "unverifieduser", "Unverified User");

        // 預置驗證連結 token（使用 StringRedisTemplate，因為 verifyByToken 以 StringRedisTemplate 讀取）
        String token = UUID.randomUUID().toString();
        String redisKey = "email:verify:link:" + token;
        stringRedisTemplate.opsForValue().set(redisKey, "unverified@example.com");

        // 呼叫 Email 驗證端點
        VerifyEmailTokenRequest req = new VerifyEmailTokenRequest();
        req.setToken(token);
        ResponseEntity<CustomApiResponse<Void>> resp = restTemplate.exchange(
                "/api/v1/auth/email/verify", HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<CustomApiResponse<Void>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 確認 DB 中使用者已設為已驗證
        Boolean isVerified = jdbcTemplate.queryForObject(
                "SELECT is_verified FROM users WHERE email = ?",
                Boolean.class, "unverified@example.com");
        assertThat(isVerified).isTrue();

        // 確認 Redis token 已消費，不可重複使用
        assertThat(stringRedisTemplate.hasKey(redisKey)).isFalse();
    }
}