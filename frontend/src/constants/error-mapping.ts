export interface ErrorDetail {
    userMessage: string;
    actionMsg?: string;
    actionUrl?: string;
    actionLabel?: string;
    severity: 'error' | 'warning' | 'info';
    debugCode: string; // 內部錯誤代碼
    traceId?: string; // 全鏈路追蹤 ID
}

/**
 * 錯誤映射字典
 * Key: 後端回傳的 errorKey (對應 ErrorCode enum name)
 */
export const ERROR_MAPPING: Record<string, ErrorDetail> = {
    // 400 Bad Request
    "USER_ALREADY_EXISTS": {
        userMessage: "這個帳號已經被註冊過了",
        actionMsg: "您可以嘗試直接登入，或使用忘記密碼功能",
        actionUrl: "/login",
        actionLabel: "前往登入",
        severity: "warning",
        debugCode: "AUTH_001"
    },
    "EMAIL_ALREADY_EXISTS": {
        userMessage: "此電子郵件已被使用",
        actionMsg: "請確認您的 Email 是否正確，或嘗試找回密碼",
        actionUrl: "/login",
        actionLabel: "前往登入",
        severity: "warning",
        debugCode: "AUTH_002"
    },
    "INVALID_INPUT": {
        userMessage: "您輸入的資料格式不正確",
        actionMsg: "請檢查所有欄位是否符合格式要求",
        severity: "error",
        debugCode: "SYS_001"
    },
    // 401 Unauthorized
    "BAD_CREDENTIALS": {
        userMessage: "帳號或密碼輸入錯誤",
        actionMsg: "請重新檢查輸入內容，注意大小寫區分",
        severity: "error",
        debugCode: "AUTH_101"
    },
    "TOKEN_EXPIRED": {
        userMessage: "您的連線已逾時",
        actionMsg: "為了您的帳號安全，請重新登入",
        actionUrl: "/login",
        actionLabel: "重新登入",
        severity: "warning",
        debugCode: "AUTH_102"
    },
    // 403 Forbidden
    "ACCESS_DENIED": {
        userMessage: "您沒有權限執行此操作",
        actionMsg: "請聯繫系統管理員以獲取更多資訊",
        severity: "error",
        debugCode: "AUTH_403"
    },
    // 400 - Email 驗證相關
    "EMAIL_VERIFICATION_OTP_INVALID": {
        userMessage: "驗證碼不正確或已過期",
        actionMsg: "請重新點擊「寄送驗證碼」後再輸入",
        severity: "error",
        debugCode: "AUTH_021"
    },
    // 403 - Email 未驗證
    "EMAIL_NOT_VERIFIED": {
        userMessage: "此帳號的 Email 尚未驗證",
        actionMsg: "請完成 Email 驗證後再登入",
        actionUrl: "/register",
        actionLabel: "前往驗證",
        severity: "warning",
        debugCode: "AUTH_403_02"
    },
    // 400 - 密碼重設相關
    "OTP_INVALID": {
        userMessage: "驗證碼無效或已過期",
        actionMsg: "請重新申請驗證碼後再試一次",
        severity: "error",
        debugCode: "AUTH_011"
    },
    "RESET_TOKEN_INVALID": {
        userMessage: "重設連結已失效",
        actionMsg: "連結僅有效 10 分鐘，請重新申請忘記密碼流程",
        actionUrl: "/",
        actionLabel: "重新申請",
        severity: "error",
        debugCode: "AUTH_012"
    },
    // 429 - 帳號鎖定
    "ACCOUNT_LOCKED": {
        userMessage: "帳號已暫時鎖定，請 15 分鐘後再試",
        actionMsg: "連續登入失敗次數過多，帳號已被暫時保護",
        severity: "warning",
        debugCode: "AUTH_429"
    },
    // 400 - 人機驗證失敗
    "CAPTCHA_FAILED": {
        userMessage: "人機驗證失敗，請重新嘗試",
        actionMsg: "請刷新頁面後再次操作",
        severity: "error",
        debugCode: "SEC_001"
    },
    // 500 Internal Error
    "INTERNAL_ERROR": {
        userMessage: "系統目前有點忙碌",
        actionMsg: "請稍候 1 分鐘再試一次，或聯繫客服支援",
        severity: "error",
        debugCode: "SYS_500"
    }
};

/**
 * 預設錯誤 (兜底用)
 */
export const DEFAULT_ERROR: ErrorDetail = {
    userMessage: "發生預期外的錯誤",
    actionMsg: "請刷新頁面或稍後再試",
    severity: "error",
    debugCode: "SYS_999"
};
