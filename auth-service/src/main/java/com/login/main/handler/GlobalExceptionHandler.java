package com.login.main.handler;

import com.login.main.common.error.AppException;
import com.login.main.common.error.ErrorCode;
import com.login.main.dto.response.CustomApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

/**
 * 全域異常處理攔截器
 * 
 * 集中管理整個 Web 模組拋出的 Exception。
 * 提供參數驗證失敗 (API 參數不對)、商務邏輯異常與系統未知異常的標準化 JSON 回應包 (ApiResponse)。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 處理參數驗證異常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        
        log.warn("參數驗證失敗: {}", errorMessage);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CustomApiResponse.error(ErrorCode.INVALID_INPUT, "參數驗證失敗: " + errorMessage));
    }

    /**
     * 處理 @RequestParam / @PathVariable 等 method parameter 的驗證異常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return field + ": " + v.getMessage();
                })
                .collect(Collectors.joining("; "));

        log.warn("參數驗證失敗: {}", errorMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CustomApiResponse.error(ErrorCode.INVALID_INPUT, "參數驗證失敗: " + errorMessage));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        int code = errorCode.getCode();
        HttpStatus status = getHttpStatus(code);

        log.error("應用程式異常: [{} - {}]", code, errorCode.getMessage(), ex);

        return ResponseEntity.status(status)
                .body(CustomApiResponse.error(errorCode));
    }

    /**
     * 處理 Email 未驗證的登入嘗試（DisabledException 為 AuthenticationException 子類，需優先處理）
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleDisabledException(DisabledException ex) {
        log.warn("登入失敗 - 帳號 Email 尚未驗證: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(CustomApiResponse.error(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("認證失敗: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CustomApiResponse.error(ErrorCode.BAD_CREDENTIALS));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("拒絕訪問: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(CustomApiResponse.error(ErrorCode.ACCESS_DENIED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomApiResponse<Void>> handleException(Exception ex) {
        log.error("未預期的系統嚴重異常: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CustomApiResponse.error(ErrorCode.INTERNAL_ERROR, "系統繁忙，請稍後再試"));
    }

    /**
     * 映射錯誤代碼至 HTTP 狀態碼
     * 將自定義的業務錯誤碼轉換為標準的 Spring HttpStatus 列舉。
     * @param code 業務錯誤碼
     * @return 對應的 HttpStatus 物件
     */
    private HttpStatus getHttpStatus(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
