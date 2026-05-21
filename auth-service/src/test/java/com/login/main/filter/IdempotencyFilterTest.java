package com.login.main.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyFilter 的單元測試
 *
 * 涵蓋非 POST / 無 key 的直接放行、快取命中時回傳快取結果，
 * 以及快取未命中時依回應狀態碼決定是否寫入 Redis。
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private IdempotencyFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String IDEMPOTENCY_KEY = "test-uuid-1234";
    private static final String REDIS_KEY = "idempotency:" + IDEMPOTENCY_KEY;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
    }

    // =========================================================
    //  非 POST 或無 key — 直接放行
    // =========================================================

    @Nested
    class PassThrough {

        @Test
        void nonPostRequest_shouldPassThroughWithoutRedis() throws Exception {
            request.setMethod("GET");
            request.addHeader("X-Idempotency-Key", IDEMPOTENCY_KEY);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void postWithoutIdempotencyKey_shouldPassThroughWithoutRedis() throws Exception {
            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        void postWithBlankIdempotencyKey_shouldPassThroughWithoutRedis() throws Exception {
            request.addHeader("X-Idempotency-Key", "   ");

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(redisTemplate);
        }
    }

    // =========================================================
    //  快取命中 — 直接回傳快取結果，不呼叫 filterChain
    // =========================================================

    @Nested
    class CacheHit {

        @Test
        void cacheHit_shouldWriteCachedResponseAndSkipFilterChain() throws Exception {
            request.addHeader("X-Idempotency-Key", IDEMPOTENCY_KEY);
            Map<String, Object> cached = Map.of(
                    "status", 200,
                    "body", "{\"code\":200}",
                    "contentType", "application/json"
            );
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenReturn(cached);

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString()).isEqualTo("{\"code\":200}");
        }

        @Test
        void cacheHit_withNullContentType_shouldNotSetContentTypeAndSkipFilterChain() throws Exception {
            request.addHeader("X-Idempotency-Key", IDEMPOTENCY_KEY);
            // 原始回應無 Content-Type（如 204 No Content），快取時 contentType 為 null
            Map<String, Object> cached = new HashMap<>();
            cached.put("status", 200);
            cached.put("body", "");
            cached.put("contentType", null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenReturn(cached);

            filter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isNull();
        }
    }

    // =========================================================
    //  快取未命中 — 依回應狀態碼決定是否快取
    // =========================================================

    @Nested
    class CacheMiss {

        @BeforeEach
        void setUpCacheMiss() {
            request.addHeader("X-Idempotency-Key", IDEMPOTENCY_KEY);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 201, 202, 299})
        void with2xxResponse_shouldCacheResultWithTtl24Hours(int statusCode) throws Exception {
            doAnswer(invocation -> {
                HttpServletResponse resp = (HttpServletResponse) invocation.getArgument(1);
                resp.setStatus(statusCode);
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilter(request, response, filterChain);

            verify(valueOperations).set(eq(REDIS_KEY), any(), eq(Duration.ofHours(24)));
        }

        @Test
        void with300Response_shouldNotCache() throws Exception {
            doAnswer(invocation -> {
                HttpServletResponse resp = (HttpServletResponse) invocation.getArgument(1);
                resp.setStatus(300);
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilter(request, response, filterChain);

            verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        }

        @Test
        void with4xxResponse_shouldNotCache() throws Exception {
            doAnswer(invocation -> {
                HttpServletResponse resp = (HttpServletResponse) invocation.getArgument(1);
                resp.setStatus(400);
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilter(request, response, filterChain);

            verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        }

        @Test
        void with5xxResponse_shouldNotCache() throws Exception {
            doAnswer(invocation -> {
                HttpServletResponse resp = (HttpServletResponse) invocation.getArgument(1);
                resp.setStatus(500);
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilter(request, response, filterChain);

            verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        }

        @Test
        void filterChainThrowsException_shouldPropagateAndNotCache() throws Exception {
            doThrow(new ServletException("upstream error")).when(filterChain).doFilter(any(), any());

            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("upstream error");

            verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        }
    }
}