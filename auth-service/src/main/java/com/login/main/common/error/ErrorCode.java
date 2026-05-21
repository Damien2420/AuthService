package com.login.main.common.error;

public enum ErrorCode {
    // 400 Bad Request - 一般性業務錯誤
    USER_ALREADY_EXISTS(400, "User already exists"),
    INVALID_INPUT(400, "Invalid input data"),
    CREATE_USER_FAILED(400, "Failed to create user"),
    EMAIL_ALREADY_EXISTS(400, "Email already exists"),
    OAUTH2_CODE_INVALID(400, "OAuth2 authorization code is invalid or expired"),
    OTP_INVALID(400, "OTP is invalid or expired"),
    RESET_TOKEN_INVALID(400, "Reset token is invalid or expired"),
    EMAIL_VERIFICATION_OTP_INVALID(400, "Email verification OTP is invalid or expired"),

    // 401 Unauthorized - 認證失敗
    BAD_CREDENTIALS(401, "Username or password incorrect"),
    TOKEN_EXPIRED(401, "Token has expired"),
    TOKEN_INVALID(401, "Token is invalid"),
    TOKEN_REVOKED(401, "Token has been revoked"),

    // 403 Forbidden - 授權失敗
    ACCESS_DENIED(403, "Access is denied"),
    EMAIL_NOT_VERIFIED(403, "Email address has not been verified"),

    // 400 Bad Request - 人機驗證失敗
    CAPTCHA_FAILED(400, "CAPTCHA verification failed"),

    // 429 Too Many Requests - 請求頻率超限
    RATE_LIMIT_EXCEEDED(429, "Too many requests, please try again later"),
    ACCOUNT_LOCKED(429, "Account is temporarily locked due to too many failed login attempts"),

    // 404 Not Found - 找不到資源
    USER_NOT_FOUND(404, "User not found"),
    ROLE_NOT_FOUND(404, "Role not found"),
    SOCIAL_ACCOUNT_NOT_FOUND(404, "Social account not found"),

    // 400 Bad Request - 不支援的 OAuth2 提供者
    UNSUPPORTED_PROVIDER(400, "Unsupported OAuth2 provider"),

    // 400 Bad Request - 密碼相關
    INCORRECT_PASSWORD(400, "Current password is incorrect"),
    PASSWORD_REQUIRED(400, "Current password is required"),

    // 400 Bad Request - Email 驗證連結相關
    EMAIL_ALREADY_VERIFIED(400, "Email address has already been verified"),
    VERIFICATION_LINK_INVALID(400, "Verification link is invalid or expired"),

    // 500 Internal Server Error - 系統內部錯誤
    INTERNAL_ERROR(500, "Internal server error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
