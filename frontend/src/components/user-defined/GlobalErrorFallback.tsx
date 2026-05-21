import type { FallbackProps } from "react-error-boundary";
import { Button } from "@/components/animate-ui/components/buttons/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { AlertTriangle } from "lucide-react";

/**
 * 全域錯誤後備渲染頁面
 * 
 * 當 React Tree 中發生無法捕獲的重大型渲染錯誤時，由 ErrorBoundary 觸發此組件。
 * 提供友善的錯誤訊息提示與「強制重載」按鈕，防止使用者陷入白屏。
 * 
 * @param props FallbackProps 包含 error 物件與重置回呼函數
 * @returns 滿屏的錯誤回退介面
 */
export const GlobalErrorFallback = ({ error, resetErrorBoundary }: FallbackProps) => {
    return (
        <div className="flex h-screen w-full items-center justify-center bg-gray-50 p-4">
            <Card className="w-full max-w-md shadow-lg border-red-100">
                <CardHeader className="text-center pb-2">
                    <div className="flex justify-center mb-4">
                        <div className="p-3 bg-red-100 rounded-full">
                            <AlertTriangle className="size-8 text-red-600" />
                        </div>
                    </div>
                    <CardTitle className="text-xl text-red-700">哎呀！發生非預期的錯誤</CardTitle>
                    <CardDescription>
                        應用程式遇到嚴重錯誤無法繼續執行。
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="bg-red-50 p-3 rounded-md border border-red-100 text-sm text-red-800 font-mono break-all">
                        {(error as any).message}
                    </div>
                    
                    <div className="pt-2">
                        <Button 
                            onClick={resetErrorBoundary} 
                            className="w-full font-bold" 
                            variant="destructive"
                        >
                            嘗試重新載入
                        </Button>
                        <p className="text-xs text-center text-gray-400 mt-4">
                            如果您持續遇到此問題，請聯繫管理員。
                        </p>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
};
