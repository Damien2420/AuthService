package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.dto.internal.TokenInfo;
import com.login.main.entity.Role;
import com.login.main.entity.SocialAccount;
import com.login.main.entity.User;
import com.login.main.enums.Providers;
import com.login.main.enums.RoleType;
import com.login.main.repository.RoleRepository;
import com.login.main.repository.UserRepository;
import com.login.main.security.CustomUserDetails;
import com.login.main.service.AuthService;
import com.login.main.service.EmailVerificationService;
import com.login.main.service.GoogleIdTokenService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.SocialAccountService;
import com.login.main.service.TokenBlacklistService;
import com.login.main.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
 * 認證服務實作類
 *
 * 核心業務邏輯層，處理帳號密碼登入、社交帳號登入、註冊、Token 換發與登出。
 * 封裝與 UserRepository、JwtUtil 及各 Redis 服務的互動細節。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleIdTokenService googleIdTokenService;
    private final SocialAccountService socialAccountService;

    /**
     * 帳號密碼登入 - 生成 JWT Token
     *
     * 直接從 SecurityContext 取得 userId 與 email。
     *
     * @param username   已通過驗證的使用者名稱
     * @param rememberMe 是否延長 RefreshToken 有效期
     * @return 包含 Access/Refresh Token 的 Result 物件
     */
    @Override
    public Result<TokenInfo> login(String username, boolean rememberMe) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            log.error("SecurityContext 中找不到 CustomUserDetails，可能是資料狀態異常 - 使用者: {}", username);
            return Result.fail(ErrorCode.INTERNAL_ERROR);
        }
        return Result.success(jwtUtil.generateTokenResponse(
                userDetails.getUserId(), userDetails.getEmail(), username, rememberMe));
    }

    /**
     * Google GIS One Tap 登入
     *
     * 驗證 Google ID Token，查詢或自動建立對應帳號，最後生成並回傳 JWT Token。
     *
     * @param googleIdToken 前端透過 Google Identity Services 取得的 ID Token
     * @return 包含 Access/Refresh Token 的 Result 物件
     */
    @Override
    public Result<TokenInfo> loginWithGoogle(String googleIdToken) {
        return googleIdTokenService.verify(googleIdToken)
                .bind(userInfo -> findOrCreateGoogleUser(userInfo))
                .map(user -> jwtUtil.generateTokenResponse(user.getID(), user.getEmail(), user.getUsername(), false));
    }

    @Override
    public Result<User> save(User user) {
        return Result.success(userRepository.save(user));
    }

    /**
     * 處理社交帳號登入與自動註冊
     *
     * 根據社交平台傳回的 Email 判斷：
     * 系統不存在該 Email：建立新 User 實體並與該社交帳號綁定。
     * 系統已存在該 Email：獲取 User 並確認是否已綁定該平台，未綁定則執行連動綁定。
     *
     * @param email      從 OAuth2 服務獲取的信箱
     * @param username   社交平台的顯示名稱（nickname）
     * @param provider   社交供應商（如 GOOGLE, DISCORD, LINE）
     * @param providerId 社交商提供的唯一代碼
     * @return 成功則回傳綁定/註冊後的 User 實體，失敗則回傳錯誤訊息
     */
    @Override
    @Transactional
    public Result<User> registerSocialUser(String email, String username, Providers provider, String providerId) {
        log.info("處理社交帳號登入/註冊: {}", email);

        return userRepository.findByEmail(email)
                .map(user -> {
                    log.info("偵測到現有使用者 {}，進行社交帳號綁定或登入", user.getUsername());
                    return Result.success(user);
                })
                .orElseGet(() -> {
                    log.info("建立新的社交使用者: {}", email);
                    User newUser = User.builder()
                            .ID(UUID.randomUUID())
                            .email(email)
                            .username(email)
                            .nickname(username)
                            .password(null)
                            .isVerified(true)
                            .build();

                    String roleName = RoleType.USER.getRoleName();
                    Role userRole = roleRepository.findByName(roleName)
                            .orElseGet(() -> roleRepository.save(new Role(roleName)));
                    newUser.getRoles().add(userRole);

                    return Result.success(userRepository.save(newUser));
                })
                .onSuccess(user -> {
                    boolean alreadyLinked = user.getSocialAccounts().stream()
                            .anyMatch(sa -> sa.getProvider() == provider && sa.getProviderID().equals(providerId));

                    if (!alreadyLinked) {
                        log.info("綁定新社交帳號: {} / {}", provider, providerId);
                        SocialAccount socialAccount = SocialAccount.builder()
                                .provider(provider)
                                .providerID(providerId)
                                .email(email)
                                .user(user)
                                .build();
                        user.getSocialAccounts().add(socialAccount);
                        userRepository.save(user);
                    }
                });
    }

    /**
     * 標準帳號註冊邏輯
     *
     * 執行使用者重複性校驗，建立經密碼加密後的 User 實體，並在成功後直接生成初始 JWT 權杖回傳。
     *
     * @param email            使用者電子郵件
     * @param username         登入帳號
     * @param password         原生密碼（將於此處進行 BCrypt 加密）
     * @param verificationCode Email OTP 驗證碼
     * @return 包含 AccessToken 與 RefreshToken 的 TokenInfo 物件
     */
    @Override
    public Result<TokenInfo> register(String email, String username, String password, String verificationCode) {
        return emailVerificationService.verifyAndConsume(email, verificationCode)
               .bind(unused -> validateIfUserExists(email, username))
               .bind(unused -> createUser(email, username, password))
               .bind(this::save)
               .onSuccess(user -> log.info("使用者 {} 註冊成功", user.getUsername()))
               .onFailure(errs -> log.error("註冊失敗: {}", String.join("; ", errs)))
               .map(user -> jwtUtil.generateTokenResponse(user.getID(), user.getEmail(), user.getUsername(), false));
    }

    /**
     * 使用者登出
     *
     * 將 AccessToken 與 RefreshToken 加入黑名單，使其立即失效。
     * 採 fail-open 策略：即使 Redis 異常，登出仍視為成功。
     *
     * @param accessToken  需要吊銷的存取權杖（可為 null）
     * @param refreshToken 需要吊銷的刷新權杖（可為 null）
     * @return 永遠回傳成功的 Result
     */
    @Override
    public Result<Void> logout(String accessToken, String refreshToken) {
        blacklistToken(accessToken, "AccessToken");
        blacklistToken(refreshToken, "RefreshToken");
        log.info("登出處理完成");
        return Result.success();
    }

    /**
     * 根據 Refresh Token 換發新存取權杖
     *
     * 驗證 Refresh Token 的有效性，提取身分，並根據原有的「記住我」設定生成一組全新的 Token 包。
     *
     * @param refreshToken 客戶端持有的刷新權杖
     * @return 包含新生成的 AccessToken 與相關身分資訊
     */
    @Override
    public Result<TokenInfo> refreshToken(String refreshToken) {
        return validateRefreshToken(refreshToken)
               .bind(token -> checkBlacklist(token))
               .bind(unused -> extractAndValidateUsername(refreshToken))
               .bind(username -> checkPasswordResetInvalidation(refreshToken, username))
               .bind(username -> generateTokensAndBlacklist(refreshToken, username))
               .onSuccess(data -> log.info("Token 刷新成功: 使用者 {}", data.username()))
               .onFailure(errs -> log.error("Token 刷新失敗: {}", String.join("; ", errs)));
    }

    /**
     * 查詢或建立 Google 使用者
     *
     * 依照 Google sub（唯一 ID）查詢社交帳號，不存在則自動建立新帳號並綁定。
     *
     * @param userInfo Google ID Token 解析後的使用者資訊
     * @return 對應的 User 實體
     */
    private Result<User> findOrCreateGoogleUser(GoogleIdTokenService.GoogleUserInfo userInfo) {
        Result<SocialAccount> socialAccountResult = socialAccountService.findByProviderID(userInfo.sub());
        if (socialAccountResult.isSuccess()) {
            User user = socialAccountResult.getData().getUser();
            log.info("GIS 登入 - 既有使用者: {}", user.getUsername());
            return Result.success(user);
        }
        log.info("GIS 登入 - 建立新使用者: {}", userInfo.email());
        return registerSocialUser(userInfo.email(), userInfo.name(), Providers.GOOGLE, userInfo.sub());
    }

    /**
     * 將單一 Token 加入黑名單
     *
     * 採 fail-open 策略：解析或 Redis 操作失敗時僅記錄 error log，不中斷登出流程。
     *
     * @param token     需要吊銷的 JWT 字串（若為 null 或空白則跳過）
     * @param tokenType 用於 log 顯示的 Token 類型名稱（如 "AccessToken"）
     */
    private void blacklistToken(String token, String tokenType) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Date expiration = jwtUtil.extractClaim(token, Claims::getExpiration);
            tokenBlacklistService.blacklist(token, expiration);
            log.info("已將 {} 加入黑名單", tokenType);
        } catch (Exception e) {
            log.error("登出時 {} 黑名單加入失敗（fail-open，不影響登出）: {}", tokenType, e.getMessage());
        }
    }

    /**
     * 驗證使用者是否已存在
     *
     * @param email    郵件
     * @param username 使用者名稱
     * @return 若帳號或 Email 已存在則回傳對應錯誤碼，否則回傳 success
     */
    private Result<Void> validateIfUserExists(String email, String username) {
        log.debug("正在驗證使用者是否存在: Username={}, Email={}", username, email);
        if (userRepository.findByUsername(username).isPresent()) {
            log.debug("驗證失敗: 使用者名稱 {} 已存在", username);
            return Result.fail(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            log.debug("驗證失敗: 電子郵件 {} 已存在", email);
            return Result.fail(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        log.debug("使用者驗證通過: {} / {}", username, email);
        return Result.success();
    }

    /**
     * 建立使用者實體
     *
     * @param email    郵件
     * @param username 使用者名稱
     * @param password 原始密碼（BCrypt 加密後存入）
     * @return 包含新建 User 實體的 Result
     */
    private Result<User> createUser(String email, String username, String password) {
        User user = User.builder()
                .ID(UUID.randomUUID())
                .email(email)
                .username(username)
                .nickname(username)
                .password(passwordEncoder.encode(password))
                .isVerified(true)
                .build();

        String roleName = RoleType.USER.getRoleName();
        Role userRole = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        user.getRoles().add(userRole);
        log.debug("使用者實體建立完成: {}, 分配權限: {}", username, roleName);
        return Result.success(user);
    }

    /**
     * 驗證 Refresh Token 是否為有效字串
     *
     * @param refreshToken Refresh Token
     * @return Token 為空時回傳 TOKEN_INVALID，否則回傳 success
     */
    private Result<String> validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.fail(ErrorCode.TOKEN_INVALID);
        }
        return Result.success(refreshToken);
    }

    /**
     * 從 Refresh Token 提取並驗證使用者名稱
     *
     * @param refreshToken Refresh Token
     * @return 解析成功則回傳 username，否則回傳 TOKEN_INVALID
     */
    private Result<String> extractAndValidateUsername(String refreshToken) {
        log.debug("開始嘗試從 RefreshToken 提取使用者名稱");
        try {
            String username = jwtUtil.extractUsername(refreshToken);
            if (username != null && jwtUtil.validateToken(refreshToken, username)) {
                log.debug("Token 解析成功，使用者名稱: {}", username);
                return Result.success(username);
            }
            log.debug("Token 驗證失敗: username 為 null 或 validateToken 返回 false");
        } catch (Exception e) {
            log.debug("Token 解析過程拋出異常: {}", e.getMessage());
            return Result.fail(ErrorCode.TOKEN_INVALID);
        }
        return Result.fail(ErrorCode.TOKEN_INVALID);
    }

    /**
     * 檢查 Refresh Token 是否已在黑名單中
     *
     * Redis 異常時採 fail-closed 策略，回傳 INTERNAL_ERROR。
     *
     * @param refreshToken Refresh Token
     * @return Token 已吊銷則回傳 TOKEN_REVOKED，Redis 異常則回傳 INTERNAL_ERROR，否則傳遞 token 繼續鏈式驗證
     */
    private Result<String> checkBlacklist(String refreshToken) {
        try {
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                log.warn("拒絕已吊銷的 Refresh Token");
                return Result.fail(ErrorCode.TOKEN_REVOKED);
            }
            return Result.success(refreshToken);
        } catch (Exception e) {
            log.error("黑名單查詢失敗，拒絕 Token Refresh 請求: {}", e.getMessage());
            return Result.fail(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 檢查 Refresh Token 是否在密碼重設後已失效
     *
     * 採 fail-closed 策略：Redis 異常時拒絕刷新請求。
     *
     * @param refreshToken Refresh Token
     * @param username     使用者名稱
     * @return Token 有效則回傳 username 繼續鏈式處理，失效或異常則回傳對應錯誤碼
     */
    private Result<String> checkPasswordResetInvalidation(String refreshToken, String username) {
        try {
            Date issuedAt = jwtUtil.extractClaim(refreshToken, Claims::getIssuedAt);
            if (passwordResetService.isTokenInvalidatedByPasswordReset(username, issuedAt)) {
                log.warn("Refresh Token 在密碼重設後已失效，拒絕刷新 - 使用者: {}", username);
                return Result.fail(ErrorCode.TOKEN_REVOKED);
            }
            return Result.success(username);
        } catch (Exception e) {
            log.error("密碼重設失效查詢失敗，拒絕 Token Refresh 請求: {}", e.getMessage());
            return Result.fail(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 將舊 Refresh Token 加入黑名單，並生成新的 Token 組
     *
     * @param oldRefreshToken 舊的 Refresh Token（將被吊銷）
     * @param username        使用者名稱
     * @return 包含新 AccessToken 與 RefreshToken 的 TokenInfo
     */
    private Result<TokenInfo> generateTokensAndBlacklist(String oldRefreshToken, String username) {
        Date expiration = jwtUtil.extractClaim(oldRefreshToken, Claims::getExpiration);
        tokenBlacklistService.blacklist(oldRefreshToken, expiration);
        return generateTokens(oldRefreshToken, username);
    }

    /**
     * 依照原有的 rememberMe 設定生成新 Token 組
     *
     * 查詢使用者資料以取得最新的 userId 與 email，確保新 Token 的 claims 與資料庫同步。
     * 此處的 DB 查詢是刻意保留的，因為使用者資料（email）可能在 Token 發行後異動。
     *
     * @param refreshToken 舊 Refresh Token（用於讀取 remember claim）
     * @param username     使用者名稱
     * @return 包含新 Token 的 Result
     */
    private Result<TokenInfo> generateTokens(String refreshToken, String username) {
        Boolean rememberMe = jwtUtil.extractClaim(refreshToken, claims -> claims.get("remember", Boolean.class));
        if (rememberMe == null) {
            return Result.fail(ErrorCode.TOKEN_INVALID);
        }
        return userRepository.findByUsername(username)
                .map(user -> Result.success(jwtUtil.generateTokenResponse(user.getID(), user.getEmail(), username, rememberMe)))
                .orElseGet(() -> {
                    log.error("Token 刷新時查無對應使用者，疑似資料異常 - 使用者: {}", username);
                    return Result.fail(ErrorCode.INTERNAL_ERROR);
                });
    }
}
