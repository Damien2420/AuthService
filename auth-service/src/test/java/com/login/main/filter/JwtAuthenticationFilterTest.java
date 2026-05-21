package com.login.main.filter;

import com.login.main.service.CustomUserDetailsService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.TokenBlacklistService;
import com.login.main.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 的單元測試
 *
 * 涵蓋路徑排除、Bearer header 缺失、JWT 解析失敗、blacklist 與密碼重設失效檢查
 * 的 fail-closed 行為。所有依賴透過 Mockito 注入，不啟動 Spring context。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private AuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String VALID_JWT = "valid.jwt.token";
    private static final String VALID_USERNAME = "testuser";
    private static final String BEARER_HEADER = "Bearer " + VALID_JWT;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        // 預設使用受保護端點，路徑排除 case 會在測試內覆寫
        request.setServletPath("/api/v1/users/me");
    }

    @AfterEach
    void tearDown() {
        // 避免 SecurityContext 污染後續測試
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    //  路徑排除清單
    // =========================================================

    @Nested
    class PathExclusion {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/login/oauth2/code/google",
                "/oauth2/authorize"
        })
        void excludedPath_shouldPassThroughWithoutParsingJwt(String path) throws Exception {
            request.setServletPath(path);
            // 即使帶有 Authorization header 也應略過 JWT 處理
            request.addHeader("Authorization", BEARER_HEADER);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtUtil, userDetailsService, tokenBlacklistService,
                    passwordResetService, authenticationEntryPoint);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // =========================================================
    //  Authorization Header 缺失或非 Bearer 格式
    // =========================================================

    @Nested
    class HeaderHandling {

        @Test
        void noAuthorizationHeader_shouldPassThroughWithoutParsingJwt() throws Exception {
            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtUtil, userDetailsService, tokenBlacklistService,
                    passwordResetService, authenticationEntryPoint);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void nonBearerAuthorizationHeader_shouldPassThroughWithoutParsingJwt() throws Exception {
            request.addHeader("Authorization", "Token abcdef");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtUtil, userDetailsService, tokenBlacklistService,
                    passwordResetService, authenticationEntryPoint);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // =========================================================
    //  JWT 解析失敗
    // =========================================================

    @Nested
    class JwtParsing {

        @Test
        void jwtParsingFails_shouldPassThroughWithoutSettingSecurityContext() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT))
                    .thenThrow(new RuntimeException("malformed token"));

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(userDetailsService, tokenBlacklistService,
                    passwordResetService, authenticationEntryPoint);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // =========================================================
    //  Token 驗證 (validateToken)
    // =========================================================

    @Nested
    class Validation {

        @Test
        void validJwt_notBlacklisted_notInvalidated_shouldSetSecurityContext() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT)).thenReturn(VALID_USERNAME);
            when(userDetailsService.loadUserByUsername(VALID_USERNAME)).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn(VALID_USERNAME);
            when(jwtUtil.validateToken(VALID_JWT, VALID_USERNAME)).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(VALID_JWT)).thenReturn(false);
            when(jwtUtil.extractClaim(eq(VALID_JWT), any())).thenReturn(new Date());
            when(passwordResetService.isTokenInvalidatedByPasswordReset(eq(VALID_USERNAME), any(Date.class)))
                    .thenReturn(false);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(authenticationEntryPoint);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(userDetails);
        }

        @Test
        void validateTokenReturnsFalse_shouldPassThroughWithoutSettingSecurityContext() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT)).thenReturn(VALID_USERNAME);
            when(userDetailsService.loadUserByUsername(VALID_USERNAME)).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn(VALID_USERNAME);
            when(jwtUtil.validateToken(VALID_JWT, VALID_USERNAME)).thenReturn(false);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(tokenBlacklistService, passwordResetService, authenticationEntryPoint);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // =========================================================
    //  Blacklist 檢查 (fail-closed)
    // =========================================================

    @Nested
    class BlacklistChecks {

        @Test
        void jwtIsBlacklisted_shouldInvokeEntryPointAndNotPassThrough() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT)).thenReturn(VALID_USERNAME);
            when(userDetailsService.loadUserByUsername(VALID_USERNAME)).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn(VALID_USERNAME);
            when(jwtUtil.validateToken(VALID_JWT, VALID_USERNAME)).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(VALID_JWT)).thenReturn(true);

            filter.doFilter(request, response, filterChain);

            verify(authenticationEntryPoint).commence(eq(request), eq(response),
                    any(InsufficientAuthenticationException.class));
            verify(filterChain, never()).doFilter(request, response);
            verifyNoInteractions(passwordResetService);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void blacklistCheckThrowsException_failClosed_shouldInvokeEntryPoint() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT)).thenReturn(VALID_USERNAME);
            when(userDetailsService.loadUserByUsername(VALID_USERNAME)).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn(VALID_USERNAME);
            when(jwtUtil.validateToken(VALID_JWT, VALID_USERNAME)).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(VALID_JWT))
                    .thenThrow(new RuntimeException("Redis connection failed"));

            filter.doFilter(request, response, filterChain);

            verify(authenticationEntryPoint).commence(eq(request), eq(response),
                    any(InsufficientAuthenticationException.class));
            verify(filterChain, never()).doFilter(request, response);
            verifyNoInteractions(passwordResetService);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // =========================================================
    //  密碼重設失效檢查 (fail-closed)
    // =========================================================

    @Nested
    class PasswordResetInvalidation {

        @Test
        void tokenIssuedBeforePasswordReset_shouldInvokeEntryPointAndNotPassThrough() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT)).thenReturn(VALID_USERNAME);
            when(userDetailsService.loadUserByUsername(VALID_USERNAME)).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn(VALID_USERNAME);
            when(jwtUtil.validateToken(VALID_JWT, VALID_USERNAME)).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(VALID_JWT)).thenReturn(false);
            when(jwtUtil.extractClaim(eq(VALID_JWT), any())).thenReturn(new Date());
            when(passwordResetService.isTokenInvalidatedByPasswordReset(eq(VALID_USERNAME), any(Date.class)))
                    .thenReturn(true);

            filter.doFilter(request, response, filterChain);

            verify(authenticationEntryPoint).commence(eq(request), eq(response),
                    any(InsufficientAuthenticationException.class));
            verify(filterChain, never()).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void passwordResetInvalidationCheckThrowsException_failClosed_shouldInvokeEntryPoint() throws Exception {
            request.addHeader("Authorization", BEARER_HEADER);
            when(jwtUtil.extractUsername(VALID_JWT)).thenReturn(VALID_USERNAME);
            when(userDetailsService.loadUserByUsername(VALID_USERNAME)).thenReturn(userDetails);
            when(userDetails.getUsername()).thenReturn(VALID_USERNAME);
            when(jwtUtil.validateToken(VALID_JWT, VALID_USERNAME)).thenReturn(true);
            when(tokenBlacklistService.isBlacklisted(VALID_JWT)).thenReturn(false);
            when(jwtUtil.extractClaim(eq(VALID_JWT), any())).thenReturn(new Date());
            when(passwordResetService.isTokenInvalidatedByPasswordReset(eq(VALID_USERNAME), any(Date.class)))
                    .thenThrow(new RuntimeException("Redis connection failed"));

            filter.doFilter(request, response, filterChain);

            verify(authenticationEntryPoint).commence(eq(request), eq(response),
                    any(InsufficientAuthenticationException.class));
            verify(filterChain, never()).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
