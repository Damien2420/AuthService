/**
 * API 端點定義
 */
export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: "/api/v1/auth/login",
    REGISTER: "/api/v1/auth/register",
    REFRESH: "/api/v1/auth/refresh",
    OAUTH2_TOKEN: "/api/v1/auth/oauth2/token",
    LOGOUT: "/api/v1/auth/logout",
    GOOGLE_LOGIN: "/api/v1/auth/google/login",
    EMAIL: {
      SEND_VERIFICATION: "/api/v1/auth/email/send-verification",
      RESEND_VERIFICATION: "/api/v1/auth/email/resend-verification",
      VERIFY: "/api/v1/auth/email/verify",
    },
    PASSWORD: {
      FORGOT: "/api/v1/auth/password/forgot",
      VERIFY_OTP: "/api/v1/auth/password/verify-otp",
      RESET: "/api/v1/auth/password/reset",
    },
  },
} as const;
