import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { Button } from "@/components/animate-ui/components/buttons/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { handleApiError } from "@/utils/api-error-handler";
import ApiClient from "@/utils/api-util";

type VerifyState = "loading" | "success" | "error";

/**
 * Email 驗證頁面組件
 *
 * 從 URL query string 取得 token，掛載時呼叫後端驗證 API。
 * 依驗證結果顯示成功或失敗訊息，成功後提供登入按鈕導向登入頁。
 *
 * @returns 渲染後的驗證狀態卡片
 */
const VerifyEmail = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const [state, setState] = useState<VerifyState>("loading");
    const [errorMessage, setErrorMessage] = useState<string>("");

    useEffect(() => {
        const token = searchParams.get("token");

        if (!token) {
            setErrorMessage("驗證連結格式不正確");
            setState("error");
            return;
        }

        ApiClient.verifyEmail(token)
            .then(() => {
                setTimeout(() => setState("success"), 3000);
            })
            .catch((error: unknown) => {
                const errorDetail = handleApiError(error);
                setErrorMessage(errorDetail.userMessage);
                setTimeout(() => setState("error"), 3000);
            });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return (
        <div className="min-h-screen flex items-center justify-center bg-background px-4">
            <Card className="w-full max-w-sm shadow-lg">
                <CardHeader className="pb-2">
                    <CardTitle className="text-xl font-bold text-center">
                        Email 驗證
                    </CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col items-center gap-6 py-6">
                    {state === "loading" && (
                        <>
                            <Spinner className="size-8" />
                            <p className="text-muted-foreground text-sm">驗證中，請稍候...</p>
                        </>
                    )}

                    {state === "success" && (
                        <>
                            <p className="text-center text-sm text-foreground">
                                Email 驗證成功，請從登入頁登入
                            </p>
                            <Button
                                className="w-full font-bold"
                                onClick={() => navigate("/")}
                            >
                                前往登入
                            </Button>
                        </>
                    )}

                    {state === "error" && (
                        <>
                            <p className="text-center text-sm text-destructive">
                                {errorMessage || "連結已失效或過期，請重新申請驗證信"}
                            </p>
                            <Button
                                variant="outline"
                                className="w-full"
                                onClick={() => navigate("/")}
                            >
                                返回登入頁
                            </Button>
                        </>
                    )}
                </CardContent>
            </Card>
        </div>
    );
};

export default VerifyEmail;
