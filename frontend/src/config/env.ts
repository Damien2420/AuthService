/**
 * 應用程式環境變數
 *
 * 集中讀取並驗證所有必要的 VITE_ 環境變數。
 * 其他模組應從此處 import，不直接讀取 import.meta.env。
 *
 * 注意：Vite 的環境變數替換為靜態分析，因此每個變數必須以字面量形式傳入。
 */

const requireEnv = (value: string | undefined, name: string): string => {
    if (!value) {
        if (import.meta.env.DEV) {
            throw new Error(`缺少必要環境變數：${name}`);
        }
        throw new Error("Application configuration error.");
    }
    return value;
};

export const ENV = {
    API_BASE_URL:       requireEnv(import.meta.env.VITE_API_BASE_URL,     "VITE_API_BASE_URL"),
    GOOGLE_CLIENT_ID:   requireEnv(import.meta.env.VITE_GOOGLE_CLIENT_ID, "VITE_GOOGLE_CLIENT_ID"),
    TURNSTILE_SITE_KEY: import.meta.env.VITE_TURNSTILE_SITE_KEY ?? '',
    SENTRY_DSN:         import.meta.env.VITE_SENTRY_DSN as string | undefined,
} as const;
