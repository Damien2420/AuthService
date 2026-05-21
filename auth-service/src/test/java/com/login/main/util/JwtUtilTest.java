package com.login.main.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.login.main.dto.internal.TokenInfo;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

@ExtendWith(MockitoExtension.class)
public class JwtUtilTest {
    private JwtUtil jwtUtil;

    private static final String TEST_SECRET =
        "c2VjcmV0LWtleS1mb3ItamF2YS1zcHJpbmctYm9vdC1qd3QtYXV0aC1kZW1vLWtleQ==";
    private static final long ACCESS_EXPIRY_MS = 60000L;
    private static final long REFRESH_EXPIRY_MS = 3600000L;
    private static final long EXPIRY_7_DAYS = 7L * 24 * 60 * 60 * 1000;
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKeyString", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", ACCESS_EXPIRY_MS);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpirationMs", REFRESH_EXPIRY_MS);
    }

    @Test
    void generateTokenResponse_accessToken_shouldContainCorrectEmail() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);

        String emailInToken = jwtUtil.extractClaim(tokenInfo.accessToken(), claims -> claims.get("email", String.class));
        assertThat(emailInToken).isEqualTo(TEST_EMAIL);
    }

    @Test
    void generateTokenResponse_accessToken_shouldContainCorrectUserId() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);

        String userIdToken = jwtUtil.extractClaim(tokenInfo.accessToken(), claims -> claims.get("userId", String.class));
        assertThat(userIdToken).isEqualTo(TEST_USER_ID.toString());
    }

    @Test
    void generateTokenResponse_refreshToken_rememberMeFalse_shouldExpireAroundRefreshExpiryMs() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);

        Date expiryDate = jwtUtil.extractClaim(tokenInfo.refreshToken(), Claims::getExpiration);
        Date expectedExpireDate = new Date(System.currentTimeMillis() + REFRESH_EXPIRY_MS);
        assertThat(expiryDate).isCloseTo(expectedExpireDate, 1000L);
    }

    @Test
    void generateTokenResponse_refreshToken_rememberMeTrue_shouldExpireAroundSevenDays() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, true);

        Date expiryDate = jwtUtil.extractClaim(tokenInfo.refreshToken(), Claims::getExpiration);
        Date expectedExpireDate = new Date(System.currentTimeMillis() + EXPIRY_7_DAYS);
        assertThat(expiryDate).isCloseTo(expectedExpireDate, 1000L);
    }

    @Test
    void generateTokenResponse_refreshToken_shouldRememberMeBeTrue() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, true);
        Boolean rememberMeClaim = jwtUtil.extractClaim(tokenInfo.refreshToken(), claims -> claims.get("remember", Boolean.class));

        assertThat(rememberMeClaim).isTrue();
    }

    @Test
    void generateTokenResponse_refreshToken_shouldRememberMeBeFalse() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);
        Boolean rememberMeClaim = jwtUtil.extractClaim(tokenInfo.refreshToken(), claims -> claims.get("remember", Boolean.class));

        assertThat(rememberMeClaim).isFalse();
    }

    @Test
    void validateToken_withValidToken_shouldReturnTrue() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);
        boolean validateResult = jwtUtil.validateToken(tokenInfo.accessToken(), TEST_USERNAME);
        assertThat(validateResult).isTrue();
    }

    @Test
    void validateToken_withWrongUsername_shouldReturnFalse() {
        String anotherUsername = "anotherOne";

        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);
        boolean validateResult = jwtUtil.validateToken(tokenInfo.accessToken(), anotherUsername);
        assertThat(validateResult).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_shouldThrowExpiredJwtException() {
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", -1000L);
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);

        assertThatThrownBy(() -> jwtUtil.validateToken(tokenInfo.accessToken(), TEST_USERNAME)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractUsername_shouldReturnCorrectSubject() {
        TokenInfo tokenInfo = jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false);

        String username = jwtUtil.extractUsername(tokenInfo.accessToken());
        assertThat(username).isEqualTo(TEST_USERNAME);
    }
}
