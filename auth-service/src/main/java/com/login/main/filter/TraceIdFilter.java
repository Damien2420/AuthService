package com.login.main.filter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 全鏈路追蹤過濾器
 * 
 * 負責從請求標頭獲取 Trace ID (X-Request-ID)，若無則生成新的 UUID。
 * 將此 ID 注入 MDC (Mapped Diagnostic Context) 以利日誌追蹤，並回傳至 Response Header 中。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Request-ID";
    private static final String MDC_TRACE_ID_KEY = "traceId";

    /**
     * 執行追蹤碼注入過濾
     * 
     * 管理 Trace ID 的生命週期，確保每個請求都有唯一的標識符存在於系統日誌中。
     * @param request HttpServletRequest 請求物件
     * @param response HttpServletResponse 回應物件
     * @param filterChain 過濾器鏈
     * @throws ServletException Servlet 處理異常
     * @throws IOException IO 操作異常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            // 從 Request Header 取得 Trace ID
            String traceId = request.getHeader(TRACE_ID_HEADER);

            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }

            // 放入 MDC
            MDC.put(MDC_TRACE_ID_KEY, traceId);

            // 放入 Response Header
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            // 清除 MDC，避免執行緒重用導致資料污染
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
}
