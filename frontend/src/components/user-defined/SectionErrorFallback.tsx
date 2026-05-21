import type { FallbackProps } from "react-error-boundary";
import { Button } from "@/components/animate-ui/components/buttons/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AlertTriangle } from "lucide-react";

/**
 * 區域錯誤後備組件
 *
 * 用於頁面內局部區域的 Error Boundary Fallback。
 * 當特定區塊發生渲染錯誤時，僅該區域顯示此錯誤介面，其他區域不受影響。
 *
 * @param props FallbackProps 包含 error 物件與重置回呼函數
 * @returns 區域性的錯誤回退介面
 */
export const SectionErrorFallback = ({ error, resetErrorBoundary }: FallbackProps) => {
    return (
        <Card className="w-full border-red-200 bg-red-50/50">
            <CardHeader className="pb-3">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-red-100 rounded-full">
                        <AlertTriangle className="size-5 text-red-600" />
                    </div>
                    <CardTitle className="text-base text-red-700">此區塊發生錯誤</CardTitle>
                </div>
            </CardHeader>
            <CardContent className="space-y-3">
                <div className="bg-red-100 p-2.5 rounded-md text-xs text-red-800 font-mono break-all">
                    {(error as any).message}
                </div>
                <Button
                    onClick={resetErrorBoundary}
                    size="sm"
                    variant="destructive"
                    className="w-full"
                >
                    重試
                </Button>
            </CardContent>
        </Card>
    );
};
