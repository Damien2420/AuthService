package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.dto.internal.TokenInfo;
import com.login.main.entity.Role;
import com.login.main.entity.SocialAccount;
import com.login.main.entity.User;
import com.login.main.enums.Providers;
import com.login.main.repository.RoleRepository;
import com.login.main.repository.UserRepository;
import com.login.main.security.CustomUserDetails;
import com.login.main.service.EmailVerificationService;
import com.login.main.service.GoogleIdTokenService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.SocialAccountService;
import com.login.main.service.TokenBlacklistService;
import com.login.main.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.jsonwebtoken.Claims;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
public class AuthServiceTest {

    @Mock 
    private UserRepository userRepository;

    @Mock 
    private RoleRepository roleRepository;

    @Mock 
    private PasswordEncoder passwordEncoder;

    @Mock 
    private JwtUtil jwtUtil;

    @Mock 
    private TokenBlacklistService tokenBlacklistService;

    @Mock 
    private PasswordResetService passwordResetService;

    @Mock 
    private EmailVerificationService emailVerificationService;

    @Mock 
    private GoogleIdTokenService googleIdTokenService;

    @Mock 
    private SocialAccountService socialAccountService;
    

    @InjectMocks
    private AuthServiceImpl authService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_USERNAME = "testuser";
    private static final String REFRESH_TOKEN = "valid-refresh-token";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ===== login =====

    @Test
    void login_whenCustomUserDetailsPresent_shouldReturnTokenInfo() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(TEST_USER_ID);
        when(userDetails.getEmail()).thenReturn(TEST_EMAIL);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));

        TokenInfo mockToken = mock(TokenInfo.class);
        when(jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false))
                .thenReturn(mockToken);

        Result<TokenInfo> result = authService.login(TEST_USERNAME, false);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockToken);
    }

    @Test
    void login_whenPrincipalIsNotCustomUserDetails_shouldReturnInternalError() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));

        Result<TokenInfo> result = authService.login(TEST_USERNAME, false);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    // ===== logout =====

    @Test
    void logout_whenBothTokensValid_shouldBlacklistBothAndReturnSuccess() {
        String accessToken = "valid-access-token";
        String refreshToken = "valid-refresh-token";
        Date expiration = new Date(System.currentTimeMillis() + 60000);

        when(jwtUtil.extractClaim(eq(accessToken), any(Function.class))).thenReturn(expiration);
        when(jwtUtil.extractClaim(eq(refreshToken), any(Function.class))).thenReturn(expiration);

        Result<Void> result = authService.logout(accessToken, refreshToken);

        assertThat(result.isSuccess()).isTrue();
        verify(tokenBlacklistService, times(1)).blacklist(eq(accessToken), eq(expiration));
        verify(tokenBlacklistService, times(1)).blacklist(eq(refreshToken), eq(expiration));
    }

    @Test
    void logout_whenTokenIsNull_shouldSkipBlacklistAndReturnSuccess() {
        Result<Void> result = authService.logout(null, null);

        assertThat(result.isSuccess()).isTrue();
        verify(tokenBlacklistService, never()).blacklist(any(), any());
    }

    @Test
    void logout_whenBlacklistThrows_shouldStillReturnSuccess() {
        String accessToken = "valid-access-token";
        Date expiration = new Date(System.currentTimeMillis() + 60000);

        when(jwtUtil.extractClaim(eq(accessToken), any(Function.class))).thenReturn(expiration);
        doThrow(new RuntimeException("Redis 連線失敗")).when(tokenBlacklistService).blacklist(eq(accessToken), any(Date.class));

        Result<Void> result = authService.logout(accessToken, null);

        assertThat(result.isSuccess()).isTrue();
    }

    // ===== register =====

    @Test
    void register_whenOtpInvalid_shouldReturnFail() {
        when(emailVerificationService.verifyAndConsume(anyString(), anyString()))
                .thenReturn(Result.fail(ErrorCode.EMAIL_VERIFICATION_OTP_INVALID));

        Result<TokenInfo> result = authService.register(TEST_EMAIL, TEST_USERNAME, "password", "wrong-otp");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_OTP_INVALID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenUsernameExists_shouldReturnFail() {
        when(emailVerificationService.verifyAndConsume(anyString(), anyString()))
                .thenReturn(Result.success());
        when(userRepository.findByUsername(TEST_USERNAME))
                .thenReturn(Optional.of(new User()));

        Result<TokenInfo> result = authService.register(TEST_EMAIL, TEST_USERNAME, "password", "otp");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenEmailExists_shouldReturnFail() {
        when(emailVerificationService.verifyAndConsume(anyString(), anyString()))
                .thenReturn(Result.success());
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(new User()));

        Result<TokenInfo> result = authService.register(TEST_EMAIL, TEST_USERNAME, "password", "otp");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_whenSuccess_shouldReturnTokenInfo() {
        Role role = new Role("ROLE_USER");
        User savedUser = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_USERNAME).build();
        TokenInfo mockToken = new TokenInfo("access-token", "refresh-token", TEST_USERNAME, 86400);

        when(emailVerificationService.verifyAndConsume(anyString(), anyString()))
                .thenReturn(Result.success());
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(roleRepository.findByName(anyString())).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false))
                .thenReturn(mockToken);

        Result<TokenInfo> result = authService.register(TEST_EMAIL, TEST_USERNAME, "password", "otp");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockToken);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRoles())
                .extracting(Role::getName)
                .contains("ROLE_USER");
    }

    // ===== refreshToken =====

    @Test
    void refreshToken_whenTokenIsNull_shouldReturnTokenInvalid() {
        Result<TokenInfo> result = authService.refreshToken(null);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
        verify(tokenBlacklistService, never()).isBlacklisted(any());
    }

    @Test
    void refreshToken_whenTokenIsBlacklisted_shouldReturnTokenRevoked() {
        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(true);

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TOKEN_REVOKED);
    }

    @Test
    void refreshToken_whenBlacklistThrows_shouldReturnInternalError() {
        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN))
                .thenThrow(new RuntimeException("Redis 連線失敗"));

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void refreshToken_whenTokenParsingFails_shouldReturnTokenInvalid() {
        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenThrow(new RuntimeException("invalid token"));

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void refreshToken_whenInvalidatedByPasswordReset_shouldReturnTokenRevoked() {
        Date issuedAt = new Date(System.currentTimeMillis() - 10000);

        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtUtil.validateToken(REFRESH_TOKEN, TEST_USERNAME)).thenReturn(true);
        when(jwtUtil.extractClaim(eq(REFRESH_TOKEN), any(Function.class))).thenReturn(issuedAt);
        when(passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt)).thenReturn(true);

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TOKEN_REVOKED);
    }

    @Test
    void refreshToken_whenPasswordResetCheckThrows_shouldReturnInternalError() {
        Date issuedAt = new Date(System.currentTimeMillis() - 10000);

        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtUtil.validateToken(REFRESH_TOKEN, TEST_USERNAME)).thenReturn(true);
        when(jwtUtil.extractClaim(eq(REFRESH_TOKEN), any(Function.class))).thenReturn(issuedAt);
        when(passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt))
                .thenThrow(new RuntimeException("Redis 連線失敗"));

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void refreshToken_whenUserNotFound_shouldReturnInternalError() {
        Date issuedAt = new Date(System.currentTimeMillis() - 10000);
        Date expiration = new Date(System.currentTimeMillis() + 3600000);

        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtUtil.validateToken(REFRESH_TOKEN, TEST_USERNAME)).thenReturn(true);
        when(jwtUtil.extractClaim(eq(REFRESH_TOKEN), any(Function.class)))
                .thenReturn(issuedAt)
                .thenReturn(expiration)
                .thenReturn(false);
        when(passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt)).thenReturn(false);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        verify(userRepository).findByUsername(TEST_USERNAME);
        verify(tokenBlacklistService).blacklist(eq(REFRESH_TOKEN), eq(expiration));
    }

    @Test
    void refreshToken_whenSuccess_shouldReturnNewTokenInfo() {
        Date issuedAt = new Date(System.currentTimeMillis() - 10000);
        Date expiration = new Date(System.currentTimeMillis() + 3600000);
        User user = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_USERNAME).build();
        TokenInfo newToken = new TokenInfo("new-access", "new-refresh", TEST_USERNAME, 86400);

        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtUtil.validateToken(REFRESH_TOKEN, TEST_USERNAME)).thenReturn(true);
        Claims mockClaims = mock(Claims.class);
        when(mockClaims.getIssuedAt()).thenReturn(issuedAt);
        when(mockClaims.getExpiration()).thenReturn(expiration);
        when(mockClaims.get("remember", Boolean.class)).thenReturn(false);
        when(jwtUtil.extractClaim(eq(REFRESH_TOKEN), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Claims, ?> fn = invocation.getArgument(1);
                    return fn.apply(mockClaims);
                });
        when(passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt)).thenReturn(false);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false)).thenReturn(newToken);

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(newToken);
        verify(tokenBlacklistService).blacklist(eq(REFRESH_TOKEN), eq(expiration));
    }

    @Test
    void refreshToken_whenRememberMeTrue_shouldPropagateRememberFlagToNewToken() {
        Date issuedAt = new Date(System.currentTimeMillis() - 10000);
        Date expiration = new Date(System.currentTimeMillis() + 604800000L);
        User user = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_USERNAME).build();
        TokenInfo newToken = new TokenInfo("new-access", "new-refresh", TEST_USERNAME, 86400);

        when(tokenBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtUtil.validateToken(REFRESH_TOKEN, TEST_USERNAME)).thenReturn(true);
        Claims mockClaims = mock(Claims.class);
        when(mockClaims.getIssuedAt()).thenReturn(issuedAt);
        when(mockClaims.getExpiration()).thenReturn(expiration);
        when(mockClaims.get("remember", Boolean.class)).thenReturn(true);
        when(jwtUtil.extractClaim(eq(REFRESH_TOKEN), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Claims, ?> fn = invocation.getArgument(1);
                    return fn.apply(mockClaims);
                });
        when(passwordResetService.isTokenInvalidatedByPasswordReset(TEST_USERNAME, issuedAt))
                .thenReturn(false);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, true))
                .thenReturn(newToken);

        Result<TokenInfo> result = authService.refreshToken(REFRESH_TOKEN);

        assertThat(result.isSuccess()).isTrue();
        verify(jwtUtil).generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, true);
    }

    // ===== loginWithGoogle =====

    @Test
    void loginWithGoogle_whenGoogleTokenInvalid_shouldReturnTokenInvalid() {
        when(googleIdTokenService.verify(anyString()))
                .thenReturn(Result.fail(ErrorCode.TOKEN_INVALID));

        Result<TokenInfo> result = authService.loginWithGoogle("invalid-google-token");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void loginWithGoogle_whenSocialAccountExists_shouldReturnTokenInfo() {
        GoogleIdTokenService.GoogleUserInfo userInfo =
                new GoogleIdTokenService.GoogleUserInfo("google-sub", TEST_EMAIL, "Test User");
        User user = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_USERNAME).build();
        SocialAccount socialAccount = SocialAccount.builder()
                .provider(Providers.GOOGLE).providerID("google-sub").email(TEST_EMAIL).user(user).build();
        TokenInfo mockToken = mock(TokenInfo.class);

        when(googleIdTokenService.verify(anyString())).thenReturn(Result.success(userInfo));
        when(socialAccountService.findByProviderID("google-sub")).thenReturn(Result.success(socialAccount));
        when(jwtUtil.generateTokenResponse(TEST_USER_ID, TEST_EMAIL, TEST_USERNAME, false)).thenReturn(mockToken);

        Result<TokenInfo> result = authService.loginWithGoogle("valid-google-token");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockToken);
    }

    // ===== registerSocialUser =====

    @Test
    void registerSocialUser_whenNewEmail_shouldCreateUserAndBindSocialAccount() {
        Role role = new Role("ROLE_USER");
        User savedUser = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_EMAIL)
                .nickname(TEST_USERNAME).isVerified(true).build();

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByName(anyString())).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(savedUser);

        Result<User> result = authService.registerSocialUser(TEST_EMAIL, TEST_USERNAME, Providers.GOOGLE, "google-sub");

        assertThat(result.isSuccess()).isTrue();
        // 第一次 save 建立 user，第二次 save 綁定 social account
        verify(userRepository, times(2)).save(any());
    }

    @Test
    void registerSocialUser_whenEmailExistsAndAlreadyLinked_shouldReturnUserWithoutExtraSave() {
        SocialAccount linkedAccount = SocialAccount.builder()
                .provider(Providers.GOOGLE).providerID("google-sub").email(TEST_EMAIL).build();
        User existingUser = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_USERNAME).nickname(TEST_USERNAME).build();
        existingUser.getSocialAccounts().add(linkedAccount);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));

        Result<User> result = authService.registerSocialUser(TEST_EMAIL, TEST_USERNAME, Providers.GOOGLE, "google-sub");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(existingUser);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSocialUser_whenEmailExistsButNotLinked_shouldBindNewSocialAccount() {
        User existingUser = User.builder()
                .ID(TEST_USER_ID).email(TEST_EMAIL).username(TEST_USERNAME).nickname(TEST_USERNAME).build();

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenReturn(existingUser);

        Result<User> result = authService.registerSocialUser(TEST_EMAIL, TEST_USERNAME, Providers.GOOGLE, "google-sub");

        assertThat(result.isSuccess()).isTrue();
        verify(userRepository, times(1)).save(any());
    }
}
