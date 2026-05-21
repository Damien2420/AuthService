import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { Spinner } from "@/components/ui/spinner";
import { LoadingWrapper } from "@/components/user-defined/loading-wrapper";
import { useAuth } from "@/contexts/AuthContext";
import apiClient from "@/utils/api-util";

/**
 * OAuth2 Callback 頁面
 *
 * 負責處理 OAuth2 One-time Code Exchange 流程的最後一哩路。
 * 後端在 OAuth2 認證成功後將使用者重導至此頁面，並附帶一次性授權碼（?code={UUID}）。
 * 此頁面取出授權碼後呼叫後端 /api/v1/auth/oauth2/token，換取 JWT Token 後導向 /welcome。
 */
const OAuthCallback = () => {
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const { setAuth } = useAuth();
    // 防止開發模式下 useEffect double invocation 導致授權碼被重複消費
    const hasExchanged = useRef(false);

    useEffect(() => {
        if (hasExchanged.current) return;
        hasExchanged.current = true;

        const code = searchParams.get("code");

        if (!code) {
            navigate("/", { replace: true, state: { error: "社群登入失敗，請重新登入" } });
            return;
        }

        apiClient
            .exchangeOAuth2Code(code)
            .then((response) => {
                // 設定認證狀態到 AuthContext
                setAuth(response.data.accessToken, response.data.username);
                navigate("/welcome", { replace: true });
            })
            .catch((error) => {
                const status = error.response?.status;
                const errorMessage =
                    status === 400
                        ? "登入失敗，請重新嘗試登入"
                        : "伺服器錯誤，請聯絡管理員";
                setIsLoading(false);
                navigate("/", { replace: true, state: { error: errorMessage } });
            });
    }, [navigate, searchParams, setAuth]);

    return (
        <LoadingWrapper
            isLoading={isLoading}
            loader={
                <div className="fixed inset-0 flex flex-col items-center justify-center bg-background">
                    <Spinner className="size-10" />
                    <p className="mt-4 text-sm text-muted-foreground">正在處理登入...</p>
                </div>
            }
        >
            <div />
        </LoadingWrapper>
    );
};

export default OAuthCallback;
