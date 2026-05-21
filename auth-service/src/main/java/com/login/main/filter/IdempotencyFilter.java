package com.login.main.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 請求冪等性過濾器
 * 
 * 使用 Redis 儲存成功請求的結果。當偵測到帶有相同 X-Idempotency-Key 的 POST 請求時，
 * 直接從快取返回結果，避免重複執行導致的資料副作用（如重複扣款、重複建立帳號）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    /**
     * 執行冪等性檢查過濾
     * 
     * 攔截 POST 請求並檢查 X-Idempotency-Key Header。若快取命中則直接返回；
     * 若未命中則執行後續並在成功後緩存 Response Body。
     * @param request HttpServletRequest 請求物件
     * @param response HttpServletResponse 回應物件
     * @param filterChain 過濾器鏈
     * @throws ServletException Servlet 處理異常
     * @throws IOException IO 操作異常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 僅針對 POST 請求且包含 X-Idempotency-Key 的情況進行處理
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (!"POST".equalsIgnoreCase(request.getMethod()) || key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String redisKey = IDEMPOTENCY_PREFIX + key;

        // 檢查 Redis 是否已有快取結果
        Object cachedResponse = redisTemplate.opsForValue().get(redisKey);
        if (cachedResponse != null) {
            log.info("偵測到重複請求，Key: {}. 返回快取結果。", key);
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedMap = (Map<String, Object>) cachedResponse;
            writeCachedResponse(response, cachedMap);
            return;
        }

        // 使用 Wrapper 捕捉輸出流
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, responseWrapper);
            
            // 只有成功處理 (2xx) 的請求才進行快取
            if (responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 300) {
                byte[] responseArray = responseWrapper.getContentAsByteArray();
                String responseBody = new String(responseArray, responseWrapper.getCharacterEncoding());
                
                // 封裝結果並存入 Redis (有效期 24 小時)
                Map<String, Object> resultToCache = new HashMap<>();
                resultToCache.put("status", responseWrapper.getStatus());
                resultToCache.put("body", responseBody);
                resultToCache.put("contentType", responseWrapper.getContentType());
                redisTemplate.opsForValue().set(redisKey, resultToCache, Duration.ofHours(24));
            }

            // 將內容寫回原始 Response
            responseWrapper.copyBodyToResponse();

        } catch (Exception e) {
            log.error("IdempotencyFilter 處理出錯: ", e);
            throw e;
        }
    }

    /**
     * 寫回快取的回應結果
     * 
     * 解析 Redis 中的快取 Map 並手動填入 Response 的狀態碼、內容類型與 Body。
     * @param response HttpServletResponse 回應物件
     * @param cached 存放快取資料的 Map
     * @throws IOException 寫入回應流出錯時拋出異常
     */
    private void writeCachedResponse(HttpServletResponse response, Map<String, Object> cached) throws IOException {
        int status = (int) cached.get("status");
        String body = (String) cached.get("body");
        String contentType = (String) cached.get("contentType");

        response.setStatus(status);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.getWriter().write(body);
        response.getWriter().flush();
    }
}
