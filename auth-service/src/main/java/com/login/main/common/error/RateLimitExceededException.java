package com.login.main.common.error;

/**
 * 請求頻率超限異常
 *
 * 當客戶端在指定時間視窗內超過允許的請求次數時拋出，
 * 繼承 AppException 以統一由 GlobalExceptionHandler 處理並回傳 HTTP 429。
 */
public class RateLimitExceededException extends AppException {

    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
