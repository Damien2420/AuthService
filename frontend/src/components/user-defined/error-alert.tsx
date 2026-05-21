"use client";

import { X } from "lucide-react";

import { Alert, AlertDescription,AlertTitle } from "@/components/ui/alert";
import { BadgeAlertIcon } from "@/components/ui/badge-alert";
import { CircleCheckIcon } from "@/components/ui/circle-check";
import { CircleHelpIcon } from "@/components/ui/circle-help";
import { cn } from "@/lib/utils";

interface ErrorAlertProps {
    title: string;
    action?: string;
    severity: 'error' | 'warning' | 'info' | 'success';
    onClose: () => void;
    actionLabel?: string;
    onAction?: () => void;
}

/**
 * 錯誤提示告警組件
 *
 * 以醒目的顏色方塊顯示系統錯誤或警告訊號。支援多種嚴重程度樣式（error, warning, info, success），
 * 並根據 severity 顯示對應的 icon 增強視覺辨識度。
 *
 * @param props ErrorAlertProps 介面，包含 title, action, severity, onClose
 * @returns 渲染後的提醒 UI 區塊
 */
export const ErrorAlert = ({ title, action, severity, onClose, actionLabel, onAction }: ErrorAlertProps) => {
    // 映射 severity 到 Alert variant 與自訂樣式
    const getVariantAndStyle = () => {
        switch (severity) {
            case 'error':
                return {
                    variant: 'destructive' as const,
                    className: ''
                };
            case 'warning':
                return {
                    variant: 'default' as const,
                    className: 'border-yellow-200 bg-yellow-50 text-yellow-800 [&>svg]:text-yellow-800 dark:border-yellow-900 dark:bg-yellow-950/40 dark:text-yellow-200 dark:[&>svg]:text-yellow-200'
                };
            case 'info':
                return {
                    variant: 'default' as const,
                    className: 'border-blue-200 bg-blue-50 text-blue-800 [&>svg]:text-blue-800 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-200 dark:[&>svg]:text-blue-200'
                };
            case 'success':
                return {
                    variant: 'default' as const,
                    className: 'border-green-200 bg-green-50 text-green-800 [&>svg]:text-green-800 dark:border-green-900 dark:bg-green-950/40 dark:text-green-200 dark:[&>svg]:text-green-200'
                };
            default:
                return {
                    variant: 'destructive' as const,
                    className: ''
                };
        }
    };

    // 根據 severity 取得對應 icon
    const getIcon = () => {
        switch (severity) {
            case 'info':
                return <CircleHelpIcon size={16} />;
            case 'success':
                return <CircleCheckIcon size={16} />;
            case 'error':
            case 'warning':
                return <BadgeAlertIcon size={16} />;
        }
    };

    const { variant, className } = getVariantAndStyle();

    return (
        <Alert variant={variant} className={cn("relative mb-2 grid-cols-[1rem_1fr] gap-x-2", className)}>
            {/* 右上角關閉按鈕 */}
            <button
                onClick={onClose}
                className="absolute top-2 right-2 text-current opacity-60 hover:opacity-100 cursor-pointer transition-opacity"
                type="button"
            >
                <X size={16} />
            </button>

            {/* Icon - 獨立一欄 */}
            <div className="row-start-1 col-start-1 translate-y-0.5">
                {getIcon()}
            </div>

            <AlertTitle className="pr-6">{title}</AlertTitle>

            {(action || (actionLabel && onAction)) && (
                <AlertDescription className="opacity-90">
                    {action}
                    {actionLabel && onAction && (
                        <button
                            type="button"
                            onClick={onAction}
                            className="ml-1 underline underline-offset-2 font-medium cursor-pointer hover:opacity-75 transition-opacity"
                        >
                            {actionLabel}
                        </button>
                    )}
                </AlertDescription>
            )}
        </Alert>
    );
};
