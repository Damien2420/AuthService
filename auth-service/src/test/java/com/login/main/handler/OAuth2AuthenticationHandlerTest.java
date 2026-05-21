package com.login.main.handler;

import com.login.main.common.error.AppException;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
import com.login.main.entity.SocialAccount;
import com.login.main.entity.User;
import com.login.main.enums.Providers;
import com.login.main.service.AuthService;
import com.login.main.service.OAuth2CodeService;
import com.login.main.service.SocialAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.RedirectStrategy;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationHandlerTest {

    @Mock private AuthService authService;
    @Mock private SocialAccountService socialAccountService;
    @Mock private OAuth2CodeService oauth2CodeService;
    @Mock private AppProperties appProperties;
    @Mock private RedirectStrategy redirectStrategy;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private OAuth2AuthenticationToken authentication;
    @Mock private OAuth2User oauth2User;

    @InjectMocks
    private OAuth2AuthenticationHandler handler;

    private static final String FRONTEND_URL = "https://example.com";
    private static final String OAUTH2_CODE = "550e8400-e29b-41d4-a716-446655440000";

    private User testUser;

    @BeforeEach
    void setUp() {
        // SimpleUrlAuthenticationSuccessHandler 的 RedirectStrategy 需手動注入
        handler.setRedirectStrategy(redirectStrategy);
        when(authentication.getPrincipal()).thenReturn(oauth2User);

        testUser = User.builder()
                .ID(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .nickname("Test User")
                .build();
    }

    // --- 輔助方法 ---

    private void givenExistingSocialAccount() {
        SocialAccount socialAccount = SocialAccount.builder()
                .provider(Providers.GOOGLE)
                .providerID("any-provider-id")
                .user(testUser)
                .build();
        when(socialAccountService.findByProviderID(anyString())).thenReturn(Result.success(socialAccount));
    }

    private void givenNewSocialAccount() {
        when(socialAccountService.findByProviderID(anyString())).thenReturn(Result.fail("NOT_FOUND"));
        when(authService.registerSocialUser(anyString(), anyString(), any(Providers.class), anyString()))
                .thenReturn(Result.success(testUser));
    }

    private void givenSuccessfulCodeIssue() {
        when(appProperties.getFrontendUrl()).thenReturn(FRONTEND_URL);
        when(oauth2CodeService.issueCode(anyString())).thenReturn(Result.success(OAUTH2_CODE));
    }

    private void setUpGoogleAttributes() {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(oauth2User.<String>getAttribute("email")).thenReturn("google@example.com");
        when(oauth2User.<String>getAttribute("sub")).thenReturn("google-sub-123");
        when(oauth2User.<String>getAttribute("name")).thenReturn("Google User");
    }

    // ---

    @Nested
    class ProviderAttributeMapping {

        @Test
        void google_mapsEmailSubName_andLooksUpSocialAccountBySubId() throws IOException {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
            when(oauth2User.<String>getAttribute("email")).thenReturn("google@example.com");
            when(oauth2User.<String>getAttribute("sub")).thenReturn("google-sub-123");
            when(oauth2User.<String>getAttribute("name")).thenReturn("Google User");
            givenExistingSocialAccount();
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(socialAccountService).findByProviderID("google-sub-123");
        }

        @Test
        void discord_withEmail_mapsIdEmailGlobalName_andLooksUpByDiscordId() throws IOException {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("discord");
            when(oauth2User.<String>getAttribute("id")).thenReturn("discord-id-456");
            when(oauth2User.<String>getAttribute("email")).thenReturn("discord@example.com");
            when(oauth2User.<String>getAttribute("global_name")).thenReturn("Discord Global Name");
            givenExistingSocialAccount();
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(socialAccountService).findByProviderID("discord-id-456");
        }

        @Test
        void discord_withoutEmail_usesSyntheticEmail() throws IOException {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("discord");
            when(oauth2User.<String>getAttribute("id")).thenReturn("discord-id-456");
            when(oauth2User.<String>getAttribute("email")).thenReturn(null);
            when(oauth2User.<String>getAttribute("global_name")).thenReturn("Discord Name");
            when(socialAccountService.findByProviderID(anyString())).thenReturn(Result.fail("NOT_FOUND"));
            when(authService.registerSocialUser(anyString(), anyString(), any(), anyString()))
                    .thenReturn(Result.success(testUser));
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).registerSocialUser(
                    eq("discord_discord-id-456@noemail.invalid"), anyString(), eq(Providers.DISCORD), anyString());
        }

        @Test
        void discord_withoutGlobalName_fallsBackToUsername() throws IOException {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("discord");
            when(oauth2User.<String>getAttribute("id")).thenReturn("discord-id-456");
            when(oauth2User.<String>getAttribute("email")).thenReturn("discord@example.com");
            when(oauth2User.<String>getAttribute("global_name")).thenReturn(null);
            when(oauth2User.<String>getAttribute("username")).thenReturn("discord_username");
            when(socialAccountService.findByProviderID(anyString())).thenReturn(Result.fail("NOT_FOUND"));
            when(authService.registerSocialUser(anyString(), anyString(), any(), anyString()))
                    .thenReturn(Result.success(testUser));
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).registerSocialUser(
                    anyString(), eq("discord_username"), any(), anyString());
        }

        @Test
        void line_withEmail_mapsSubEmailName_andLooksUpBySubId() throws IOException {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("line");
            when(oauth2User.<String>getAttribute("sub")).thenReturn("line-sub-789");
            when(oauth2User.<String>getAttribute("email")).thenReturn("line@example.com");
            when(oauth2User.<String>getAttribute("name")).thenReturn("LINE User");
            givenExistingSocialAccount();
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(socialAccountService).findByProviderID("line-sub-789");
        }

        @Test
        void line_withoutEmail_usesSyntheticEmail() throws IOException {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("line");
            when(oauth2User.<String>getAttribute("sub")).thenReturn("line-sub-789");
            when(oauth2User.<String>getAttribute("email")).thenReturn(null);
            when(oauth2User.<String>getAttribute("name")).thenReturn("LINE User");
            when(socialAccountService.findByProviderID(anyString())).thenReturn(Result.fail("NOT_FOUND"));
            when(authService.registerSocialUser(anyString(), anyString(), any(), anyString()))
                    .thenReturn(Result.success(testUser));
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).registerSocialUser(
                    eq("line_line-sub-789@noemail.invalid"), anyString(), eq(Providers.LINE), anyString());
        }

        @Test
        void unsupportedProvider_throwsIllegalStateException_withRegistrationIdInMessage() {
            when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");

            assertThatThrownBy(() -> handler.onAuthenticationSuccess(request, response, authentication))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("github");
        }
    }

    @Nested
    class SocialAccountLookup {

        @BeforeEach
        void setUpGoogle() {
            setUpGoogleAttributes();
        }

        @Test
        void existingSocialAccount_skipsUserRegistration() throws IOException {
            givenExistingSocialAccount();
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService, never()).registerSocialUser(anyString(), anyString(), any(), anyString());
        }

        @Test
        void newSocialAccount_callsRegisterSocialUser_withCorrectProviderAndId() throws IOException {
            givenNewSocialAccount();
            givenSuccessfulCodeIssue();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).registerSocialUser(
                    anyString(), anyString(), eq(Providers.GOOGLE), eq("google-sub-123"));
        }

        @Test
        void registerSocialUser_fails_throwsAppException() {
            when(socialAccountService.findByProviderID(anyString())).thenReturn(Result.fail("NOT_FOUND"));
            when(authService.registerSocialUser(anyString(), anyString(), any(), anyString()))
                    .thenReturn(Result.fail("register failed"));

            assertThatThrownBy(() -> handler.onAuthenticationSuccess(request, response, authentication))
                    .isInstanceOf(AppException.class);
        }
    }

    @Nested
    class CodeIssuanceAndRedirect {

        @BeforeEach
        void setUpGoogleWithExistingAccount() {
            setUpGoogleAttributes();
            givenExistingSocialAccount();
            when(appProperties.getFrontendUrl()).thenReturn(FRONTEND_URL);
        }

        @Test
        void issueCode_succeeds_redirectsToCallbackWithCode() throws IOException {
            when(oauth2CodeService.issueCode(testUser.getUsername())).thenReturn(Result.success(OAUTH2_CODE));

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(redirectStrategy).sendRedirect(request, response,
                    FRONTEND_URL + "/auth/callback?code=" + OAUTH2_CODE);
        }

        @Test
        void issueCode_fails_redirectsToLoginWithServerError() throws IOException {
            when(oauth2CodeService.issueCode(anyString())).thenReturn(Result.fail("Redis error"));

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(redirectStrategy).sendRedirect(request, response,
                    FRONTEND_URL + "/login?error=server_error");
        }

        @Test
        void issueCode_succeeds_doesNotRedirectToErrorPage() throws IOException {
            when(oauth2CodeService.issueCode(anyString())).thenReturn(Result.success(OAUTH2_CODE));

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(redirectStrategy, never()).sendRedirect(request, response,
                    FRONTEND_URL + "/login?error=server_error");
        }
    }
}
