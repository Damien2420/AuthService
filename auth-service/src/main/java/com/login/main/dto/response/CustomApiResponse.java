package com.login.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

import org.slf4j.MDC;

/**
 * 統一 API 回傳格式
 * @param <T> 回傳資料的型別
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomApiResponse<T> {
    private boolean success;
    private String errorKey;
    private String requestId; // 全鏈路追蹤 ID
    private String message;
    private T data;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 建立成功回應
     * 
     * 封裝成功操作的資料，並自動注入全鏈路追蹤 ID 與目前時間。
     * @param data 回傳資料
     * @param <T> 資料型別
     * @return 封裝後的 CustomApiResponse
     */
    public static <T> CustomApiResponse<T> success(T data) {
        return CustomApiResponse.<T>builder()
                .success(true)
                .message("操作成功")
                .requestId(MDC.get("traceId"))
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 建立成功回應 (含自定義訊息)
     * 
     * 除回傳資料外，允許前端顯示特定的成功文字提示。
     * @param data 回傳資料
     * @param message 成功訊息
     * @param <T> 資料型別
     * @return 封裝後的 CustomApiResponse
     */
    public static <T> CustomApiResponse<T> success(T data, String message) {
        return CustomApiResponse.<T>builder()
                .success(true)
                .message(message)
                .requestId(MDC.get("traceId"))
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 建立錯誤回應 (手動指定代碼)
     * 
     * 針對非預定義的特殊錯誤情況進行快速封裝。
     * @param code 錯誤代碼 (HTTP 狀態碼或業務代碼)
     * @param message 錯誤描述
     * @param <T> 目標型別
     * @return 封裝後的失敗 ApiResponse
     */
    /**
     * 建立錯誤回應 (標準錯誤碼)
     *
     * 根據全局 ErrorCode 枚舉自動填充 errorKey 與 message。
     * @param errorCode 預定義錯誤碼物件
     * @param <T> 目標型別
     * @return 封裝後的失敗 ApiResponse
     */
    public static <T> CustomApiResponse<T> error(com.login.main.common.error.ErrorCode errorCode) {
        return CustomApiResponse.<T>builder()
                .success(false)
                .errorKey(errorCode.name())
                .message(errorCode.getMessage())
                .requestId(MDC.get("traceId"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> CustomApiResponse<T> error(com.login.main.common.error.ErrorCode errorCode, String customMessage) {
        return CustomApiResponse.<T>builder()
                .success(false)
                .errorKey(errorCode.name())
                .message(customMessage)
                .requestId(MDC.get("traceId"))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
