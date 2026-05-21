import { useNavigate } from "react-router-dom";

import { useAuth } from "@/contexts/AuthContext";
import apiClient from "@/utils/api-util";
import { logger } from "@/utils/logger";

/**
 * Welcome 頁面
 *
 * 登入成功後的首頁，顯示使用者資訊。
 * 從 AuthContext 取得認證狀態和使用者名稱。
 */
const Welcome = () => {
    const navigate = useNavigate();
    const { username, clearAuth } = useAuth();

    const handleBackToMain = () => {
        navigate("/");
    };

    /**
     * 登出處理函式
     *
     * 呼叫後端 /auth/logout API 將 Token 加入黑名單並清除 Cookie。
     * 採 fail-open：即使 API 失敗，仍清除前端認證狀態並導向首頁。
     */
    const handleLogout = async () => {
        try {
            await apiClient.logout();
        } catch (error) {
            logger.warn('[Logout] API failed', { error: String(error) });
        } finally {
            clearAuth();
            navigate("/");
        }
    };

    return (
        <div style={{ padding: "2rem", textAlign: "center" }}>
            <h1>Welcome</h1>
            {username ? (
                <div>
                    <p style={{ fontSize: "1.25rem", marginBottom: "1rem" }}>
                        歡迎回來，{username}！
                    </p>
                </div>
            ) : (
                <p style={{ marginBottom: "2rem" }}>未登入</p>
            )}
            <button onClick={handleBackToMain}>返回首頁</button>
            <button onClick={handleLogout} style={{ marginLeft: "1rem" }}>登出</button>
        </div>
    );
};

export default Welcome;
