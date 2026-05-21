import { ENV } from "../config/env";
import ApiClient, { type AuthResponse } from "./api-util";
import { logger } from "./logger";

/**
 * 初始化並觸發 GIS One Tap 提示
 *
 * 向 GIS 函式庫設定 Client ID 與登入 Callback，完成初始化後立即觸發 One Tap 提示。
 * 初始化與觸發作為原子操作，確保執行順序正確。
 * 若 GIS script 尚未載入，此函式不執行任何操作。
 *
 * @param onLoginSuccess - 登入成功時的 Callback，接收後端回傳的 accessToken 與 username
 * @param onLoginFailure - 登入失敗時的 Callback，接收錯誤物件
 */
export const initAndPromptGoogleOneTap = (
    onLoginSuccess: (data: AuthResponse) => void,
    onLoginFailure: (error: unknown) => void
) => {
    if (!window.google) return;

    // 處理 Google 驗證結果，並將 Token 傳給後端的 callback
    const handleGoogleLogin = async (googleResponse: google.accounts.id.CredentialResponse) => {
        if (!googleResponse || !googleResponse.credential) {
            logger.error('[GIS] Invalid Google response');
            if (onLoginFailure) onLoginFailure(new Error("Invalid Google response"));
            return;
        }
        try {
            // 後端驗證
            const response = await ApiClient.googleLogin(googleResponse.credential);

            if (response.success === true && response.data) {
                if (onLoginSuccess) {
                    onLoginSuccess(response.data);
                }
            } else {
                logger.error('[GIS] Backend login failed', { success: response.success });
                if (onLoginFailure) onLoginFailure(response);
            }
        } catch (error) {
            logger.error('[GIS] 後端處理 Google 登入時發生錯誤', error as Error);
            if (onLoginFailure) onLoginFailure(error);
        }
    };

    // 傳入 IdConfiguration 物件，初始化後立即觸發 One Tap 提示
    window.google.accounts.id.initialize({
        client_id: ENV.GOOGLE_CLIENT_ID,
        callback: handleGoogleLogin, // 取得 Google 回傳後執行的函式
        auto_select: false, // 自動登入
        cancel_on_tap_outside: false, // 禁止使用者按視窗外部來取消登入
        prompt_parent_id: "g-one-tap-container" // 指定 One Tap 卡片的掛載容器
    });

    window.google.accounts.id.prompt(); // 顯示由瀏覽器（透過 FedCM）管理的登入提示
};