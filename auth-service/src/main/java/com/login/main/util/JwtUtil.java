package com.login.main.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.login.main.dto.internal.TokenInfo;

import javax.crypto.SecretKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT 權杖工具類
 * 
 * 專責處理 JSON Web Token 的生成、解析與驗證。
 * 提供基於密鑰的簽署邏輯，支援區分「記住我」選項的差異化過期策略，並從 Request Header 中提取身分資訊。
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${JWT_SECRET}")
    private String secretKeyString;

    @Value("${JWT_EXPIRATION_MS}")
    private long jwtExpirationMs;

    @Value("${JWT_REFRESH_EXPIRATION_MS}")
    private long refreshExpirationMs;

    // 更長的 refresh token 有效時間
    private static final Long REFRESH_TOKEN_LONG_TERM_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * 獲取簽署密鑰
     * 
     * 將配置中的秘密字串解碼為適用於 HMAC 演算法的 SecretKey 物件。
     * @return SecretKey 實體
     */
    private SecretKey getSignInKey() {
        try {
            // 優先嘗試使用 Base64 解碼
            byte[] keyBytes = Decoders.BASE64.decode(secretKeyString);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            // 如果不是有效的 Base64，則將字串直接視為 UTF-8 位元組處理
            log.warn("JWT 秘密金鑰不是有效的 Base64 格式，將回退使用內容的 UTF-8 位元組。建議提供 Base64 編碼的金鑰以提高安全性。");
            return Keys.hmacShaKeyFor(secretKeyString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 建立 Token 基礎方法
     * 
     * 封裝 jjwt 函式庫，根據使用者名稱、過期時間與額外補給的 Claims 構建 JWT 內容。
     * @param username 使用者名稱
     * @param expiration 過期毫秒數
     * @param extraClaims 額外裝載的資料
     * @return 經簽署並序列化為標準 JWT 字串格式的 Token
     */
    private String buildToken(String username, long expiration, Map<String, Object> extraClaims) {
        Date tokenIssuedAt = new Date(System.currentTimeMillis());
        Date tokenExpirationAt = new Date(System.currentTimeMillis() + expiration);
        String token = Jwts.builder()
                .claims(extraClaims)
                .subject(username)
                .issuedAt(tokenIssuedAt)
                .expiration(tokenExpirationAt)
                .signWith(getSignInKey())
                .compact();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.debug("生成 Token 完成 - 使用者: {}, 發行時間: {}, 過期時間: {}", 
                username, sdf.format(tokenIssuedAt), sdf.format(tokenExpirationAt));
        return token;
    }

    /**
     * 從 Token 中提取使用者名稱
     * 
     * 解析 JWT Payload 並獲取其 Subject 欄位。
     * @param token JWT 字串
     * @return 使用者名稱
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 驗證 Token 是否合法
     * 
     * 檢查 Token 是否已過期，且其聲稱的使用者名稱是否與預期一致。
     * @param token JWT 字串
     * @param username 預期使用者名稱
     * @return 是否通過驗證
     */
    public boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        boolean isTokenExpired = extractClaim(token, Claims::getExpiration).before(new Date());
        boolean isTokenValid = extractedUsername.equals(username) && !isTokenExpired;
        log.debug("Token 驗證結果: {}, 使用者: {}, 是否過期: {}", isTokenValid, extractedUsername, isTokenExpired);
        return isTokenValid;
    }

    /**
     * 提取 Token 中的特定資訊 (Claim)
     * 
     * 提供一個泛型封裝，允許透過 Resolver 函式從解析出的 Claims 物件中選取特定欄位。
     * @param token JWT 字串
     * @param claimsResolver 選取邏輯函式
     * @param <T> 回傳資料型別
     * @return 提取出的資料
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                                  .verifyWith(getSignInKey())
                                  .build()
                                  .parseSignedClaims(token)
                                  .getPayload();
        return claimsResolver.apply(claims);
    }

    /**
     * 生成完整的 Token 回應
     *
     * 同時生成 AccessToken 與 RefreshToken。
     * AccessToken 的 claims 包含 userId 與 email。
     * RefreshToken 僅攜帶 rememberMe 旗標，不包含使用者資料。
     *
     * @param userId     使用者 UUID（來自 users.id）
     * @param email      使用者電子郵件
     * @param username   使用者名稱（JWT subject）
     * @param rememberMe 是否記住我（影響 RefreshToken 有效期）
     * @return 包含兩組 Token 與使用者名稱的 TokenInfo 物件
     */
    public TokenInfo generateTokenResponse(UUID userId, String email, String username, boolean rememberMe) {
        long now = System.currentTimeMillis();
        log.debug("開始生成 AccessToken - 使用者: {}, 預計過期時間: {}", username, DateTimeUtil.formatWithDuration(jwtExpirationMs, now));

        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put("userId", userId.toString());
        accessClaims.put("email", email);
        String accessToken = buildToken(username, jwtExpirationMs, accessClaims);

        log.debug("使用者是否勾選記住我: {}", rememberMe ? "是" : "否");
        long refreshExpiration = rememberMe ? REFRESH_TOKEN_LONG_TERM_EXPIRATION_MS : refreshExpirationMs;
        log.debug("開始生成 RefreshToken - 使用者: {}, 預計過期時間: {}", username, DateTimeUtil.formatWithDuration(refreshExpiration, now));
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("remember", rememberMe);
        String refreshToken = buildToken(username, refreshExpiration, refreshClaims);

        return new TokenInfo(accessToken, refreshToken, username, refreshExpiration / 1000);
    }
}
