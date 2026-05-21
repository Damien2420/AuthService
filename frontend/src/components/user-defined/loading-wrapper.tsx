import { Spinner } from "@/components/ui/spinner";
import type { ReactNode } from "react";

interface LoadingWrapperProps {
  isLoading: boolean;
  loader?: ReactNode;
  children: ReactNode;
}

/**
 * 載入狀態封裝組件
 * 
 * 根據傳入的 `isLoading` 狀態決定渲染子內容（children）或是載入動畫（Spinner）。
 * 常用於按鈕內部或小型內容區塊的非同步狀態處理。
 * 
 * @param props LoadingWrapperProps 介面，包含 isLoading 與 children
 * @returns 渲染後的內容或 Spinner 動畫
 */
export function LoadingWrapper({ isLoading, loader, children }: LoadingWrapperProps) {
  if (isLoading) {
    return <>{loader || <Spinner />}</>;
  }
  return <>{children}</>;
}
