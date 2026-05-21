package com.login.main.filter;

import com.login.main.common.error.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RateLimitFilter 的單元測試
 *
 * 涵蓋路徑過濾規則、IP 提取優先順序（XFF / X-Real-IP / remoteAddr）、
 * IPv6 正規化，以及 bucket 耗盡時的 429 處理。
 * proxyManager 透過 ReflectionTestUtils 注入，略過 @PostConstruct Lettuce 初始化。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private LettuceBasedProxyManager<String> proxyManager;

    @Mock
    private BucketProxy bucket;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String BUCKET_KEY_PREFIX = "rate_limit:login:";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "proxyManager", proxyManager);
        // bucketConfigs 由 @PostConstruct 初始化，單元測試須手動補入
        BucketConfiguration loginConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(20, Duration.ofMinutes(1)))
                .build();
        ReflectionTestUtils.setField(filter, "bucketConfigs", Map.of(LOGIN_PATH, loginConfig));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRemoteAddr("192.168.1.1");
    }

    // =========================================================
    //  shouldNotFilter — 路徑過濾規則
    // =========================================================

    @Nested
    class PathFiltering {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/api/v1/auth/oauth2/token",
                "/api/v1/auth/email/send-verification",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/verify-otp",
                "/api/v1/auth/password/reset"
        })
        void rateLimitedPath_shouldApplyFilter(String path) {
            request.setRequestURI(path);
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/users/me",
                "/api/v1/auth/logout",
                "/health"
        })
        void nonRateLimitedPath_shouldSkipFilter(String path) {
            request.setRequestURI(path);
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        void rateLimitedPathWithTrailingSlash_shouldApplyFilter() {
            // 尾隨斜線應被正規化後仍命中限流清單
            request.setRequestURI(LOGIN_PATH + "/");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    // =========================================================
    //  IP 提取優先順序
    // =========================================================

    @Nested
    class IpExtraction {

        @BeforeEach
        void setUpBucket() {
            when(proxyManager.builder().build(anyString(), any(BucketConfiguration.class))).thenReturn(bucket);
            when(bucket.tryConsume(1)).thenReturn(true);
            request.setRequestURI(LOGIN_PATH);
        }

        @Test
        void xForwardedFor_singleIp_shouldUseIt() throws Exception {
            request.addHeader("X-Forwarded-For", "10.0.0.1");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "10.0.0.1");
        }

        @Test
        void xForwardedFor_proxyChain_shouldUseFirstIp() throws Exception {
            // 格式："client, proxy1, proxy2"，只取第一個 IP
            request.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1, 192.168.0.1");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "10.0.0.1");
        }

        @Test
        void xRealIp_whenNoXForwardedFor_shouldUseIt() throws Exception {
            request.addHeader("X-Real-IP", "10.0.0.2");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "10.0.0.2");
        }

        @Test
        void noProxyHeaders_shouldFallbackToRemoteAddr() throws Exception {
            // 無代理 header 時使用 TCP 連線的 remoteAddr（setUp 預設 192.168.1.1）
            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "192.168.1.1");
        }

        @Test
        void ipv6Loopback_shortForm_shouldNormalizeToIpv4() throws Exception {
            request.setRemoteAddr("::1");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "127.0.0.1");
        }

        @Test
        void ipv6Loopback_fullForm_shouldNormalizeToIpv4() throws Exception {
            request.setRemoteAddr("0:0:0:0:0:0:0:1");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "127.0.0.1");
        }

        @Test
        void ipv6Address_shouldReplaceColonsWithDashes() throws Exception {
            // IPv6 地址中的 : 替換為 -，確保 Redis key 不破壞 namespace 結構
            // "2001:db8::1" → replace(':','-') → "2001-db8--1"
            request.addHeader("X-Forwarded-For", "2001:db8::1");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(proxyManager.builder()).build(keyCaptor.capture(), any(BucketConfiguration.class));
            assertThat(keyCaptor.getValue()).isEqualTo(BUCKET_KEY_PREFIX + "2001-db8--1");
        }
    }

    // =========================================================
    //  速率限制邏輯
    // =========================================================

    @Nested
    class RateLimiting {

        @BeforeEach
        void setUpPath() {
            request.setRequestURI(LOGIN_PATH);
            when(proxyManager.builder().build(anyString(), any(BucketConfiguration.class))).thenReturn(bucket);
        }

        @Test
        void tokenAvailable_shouldContinueFilterChain() throws Exception {
            when(bucket.tryConsume(1)).thenReturn(true);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(handlerExceptionResolver);
        }

        @Test
        void bucketExhausted_shouldSetRetryAfterHeaderAndDelegate429() throws Exception {
            when(bucket.tryConsume(1)).thenReturn(false);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("Retry-After")).isEqualTo("60");
            verify(handlerExceptionResolver).resolveException(
                    eq(request), eq(response), isNull(), any(RateLimitExceededException.class));
            verify(filterChain, never()).doFilter(any(), any());
        }
    }
}