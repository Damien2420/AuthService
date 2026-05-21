'use client';

import * as React from 'react';
import { ThemeProvider as NextThemesProvider, type ThemeProviderProps } from 'next-themes';

/**
 * 主題提供者組件
 *
 * 封裝 next-themes 的 ThemeProvider，提供統一的主題配置管理。
 *
 * @param props - ThemeProvider 的屬性
 * @param props.children - 子組件
 * @param props.defaultTheme - 預設主題（預設值："system"）
 * @param props.attribute - 主題屬性（預設值："class"）
 * @returns 主題提供者組件
 *
 * @example
 * ```tsx
 * <ThemeProvider defaultTheme="system" attribute="class">
 *   <App />
 * </ThemeProvider>
 * ```
 */
export function ThemeProvider({
  children,
  defaultTheme = 'system',
  attribute = 'class',
  ...props
}: ThemeProviderProps) {
  return (
    <NextThemesProvider
      defaultTheme={defaultTheme}
      attribute={attribute}
      {...props}
    >
      {children}
    </NextThemesProvider>
  );
}
