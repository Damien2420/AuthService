package com.login.main.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.login.main.common.error.ErrorCode;
import com.login.main.dto.response.CustomApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtErrorHandlerEntry implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        log.error("JWT 認證攔截 (401): Request URI: {}, Error: {}", request.getRequestURI(), authException.getMessage());

        // 設定 HTTP Status 401 Unauthorized
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // 封裝成 ErrorResponse 格式
        CustomApiResponse<Void> errorResponse = CustomApiResponse.error(ErrorCode.TOKEN_INVALID, "JWT 驗證失敗或無效: " + authException.getMessage());

        // 使用注入的 ObjectMapper 寫入 Response
        response.getWriter().write(mapper.writeValueAsString(errorResponse));
    }
}
