package com.login.main.filter;

import com.login.main.service.CustomUserDetailsService;
import com.login.main.service.PasswordResetService;
import com.login.main.service.TokenBlacklistService;
import com.login.main.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 身分驗證過濾器
 *
 * 負責攔截每個傳入的 HTTP 請求，從 Authorization Header 提取 JWT Token。
 * 驗證 Token 的有效性後，將使用者資訊封裝至 Spring Security 上下文中，實現無狀態驗證。
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordResetService passwordResetService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    /**
     * JWT 提取、解析與 SecurityContext 的設置。
     * @param request HttpServletRequest 請求物件
     * @param response HttpServletResponse 回應物件
     * @param filterChain 過濾器鏈
     * @throws ServletException Servlet 處理異常
     * @throws IOException IO 操作異常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        log.debug("JwtAuthenticationFilter: servletPath={}", path);
        if (path.startsWith("/api/v1/auth/") || path.startsWith("/login/oauth2/") || path.startsWith("/oauth2/")) {
            log.debug("JwtAuthenticationFilter: 路徑 {} 符合排除清單，直接放行", path);
            filterChain.doFilter(request, response);
            return;
        }

        // 從 Header 取得 Token (Authorization: Bearer <token>)
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            // Token 解析失敗直接交給 Spring Security 處理
            log.debug("JWT 解析失敗: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // 驗證 Token 並設定 SecurityContext
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validateToken(jwt, userDetails.getUsername())) {
                // 查詢黑名單，確認 Token 未被吊銷（fail-closed）
                // 命中黑名單或 Redis 異常時，直接回傳 401，不繼續 filter chain
                try {
                    if (tokenBlacklistService.isBlacklisted(jwt)) {
                        log.warn("JWT 已被吊銷，拒絕存取 - 使用者: {}", username);
                        authenticationEntryPoint.commence(request, response,
                                new InsufficientAuthenticationException("Token 已被吊銷"));
                        return;
                    }
                } catch (Exception e) {
                    log.error("黑名單查詢失敗，基於安全考量拒絕存取: {}", e.getMessage());
                    authenticationEntryPoint.commence(request, response,
                            new InsufficientAuthenticationException("黑名單服務異常，拒絕存取"));
                    return;
                }

                // 密碼重設後的 Token 失效驗證（fail-closed）
                try {
                    java.util.Date issuedAt = jwtUtil.extractClaim(jwt, Claims::getIssuedAt);
                    if (passwordResetService.isTokenInvalidatedByPasswordReset(username, issuedAt)) {
                        log.warn("Token 在密碼重設後已失效，拒絕存取 - 使用者: {}", username);
                        authenticationEntryPoint.commence(request, response,
                                new InsufficientAuthenticationException("Token 已被密碼重設作廢"));
                        return;
                    }
                } catch (Exception e) {
                    log.error("密碼重設失效查詢失敗，拒絕存取: {}", e.getMessage());
                    authenticationEntryPoint.commence(request, response,
                            new InsufficientAuthenticationException("失效檢查服務異常，拒絕存取"));
                    return;
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("JWT 驗證成功，使用者: {}, 權限: {}", username, userDetails.getAuthorities());
            }
        }

        filterChain.doFilter(request, response);
    }
}
