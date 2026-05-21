package com.login.main.handler;

import com.login.main.common.error.AppException;
import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.entity.SocialAccount;
import com.login.main.entity.User;
import com.login.main.enums.Providers;
import com.login.main.service.AuthService;
import com.login.main.service.OAuth2CodeService;
import com.login.main.service.SocialAccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.login.main.config.AppProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final SocialAccountService socialAccountService;
    private final OAuth2CodeService oauth2CodeService;
    private final AppProperties appProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // 取得 registrationId 以識別當前 OAuth2 提供者
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauth2Token.getPrincipal();
        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();

        String email;
        String providerID;
        String name;
        Providers provider;

        // 依照不同提供者映射對應的使用者屬性
        switch (registrationId) {
            case "google" -> {
                email = oauth2User.getAttribute("email");
                providerID = oauth2User.getAttribute("sub");
                name = oauth2User.getAttribute("name");
                provider = Providers.GOOGLE;
            }
            case "discord" -> {
                providerID = oauth2User.getAttribute("id");
                email = oauth2User.getAttribute("email");
                // Discord 帳號理論上必有 email，但保險起見提供合成信箱作為 fallback
                if (email == null) {
                    email = "discord_" + providerID + "@noemail.invalid";
                }
                String globalName = oauth2User.getAttribute("global_name");
                name = (globalName != null) ? globalName : oauth2User.getAttribute("username");
                provider = Providers.DISCORD;
            }
            case "line" -> {
                providerID = oauth2User.getAttribute("sub");
                email = oauth2User.getAttribute("email");
                // LINE 帳號未綁定信箱時 email 為 null，使用 sub 產生穩定的合成信箱
                if (email == null) {
                    email = "line_" + providerID + "@noemail.invalid";
                }
                name = oauth2User.getAttribute("name");
                provider = Providers.LINE;
            }
            default -> throw new IllegalStateException(
                    "OAuth2 provider '" + registrationId + "' 已在 Spring Security 設定中啟用，但 handler 尚未實作對應邏輯");
        }

        log.info("OAuth2 登入成功: Provider={}, Email={}, Name={}", provider, email, name);

        Result<SocialAccount> searchResult = socialAccountService.findByProviderID(providerID);

        User user;
        if (searchResult.isSuccess()) {
            user = searchResult.getData().getUser();
            log.info("現有社交使用者登入: {}", user.getUsername());
        } else {
            log.info("偵測到新社交使用者，開始註冊流程: {}", email);
            Result<User> registerResult = authService.registerSocialUser(email, name, provider, providerID);

            if (registerResult.isFailed()) {
                log.error("社交使用者註冊失敗: {}", String.join("; ", registerResult.getErrorMessages()));
                throw new AppException(ErrorCode.CREATE_USER_FAILED);
            }

            user = registerResult.getData();
            log.info("新社交使用者註冊完成: {}", user.getUsername());
        }

        // 發放一次性授權碼（存入 Redis，TTL 60 秒），不在 URL 中直接傳遞 JWT
        Result<String> codeResult = oauth2CodeService.issueCode(user.getUsername());

        if (codeResult.isFailed()) {
            log.error("OAuth2 授權碼發放失敗（Redis 異常），重導向至錯誤頁面 - 使用者: {}", user.getUsername());
            getRedirectStrategy().sendRedirect(request, response,
                    appProperties.getFrontendUrl() + "/login?error=server_error");
            return;
        }

        String code = codeResult.getData();
        String redirectUrl = UriComponentsBuilder.fromUriString(appProperties.getFrontendUrl() + "/auth/callback")
                .queryParam("code", code)
                .build().toUriString();

        log.info("OAuth2 一次性授權碼已發放，重導向至前端 Callback - 使用者: {}", user.getUsername());
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
