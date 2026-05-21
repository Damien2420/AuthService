import * as Sentry from '@sentry/react';

import { ENV } from '@/config/env';

const isDev = import.meta.env.DEV;

/**
 * 是否啟用 Sentry 監控
 *
 * 只要設定了 VITE_SENTRY_DSN 即啟用，不限環境。
 * Dev 環境可透過在 .env.development 填入 DSN 來測試 Sentry 功能。
 */
const isSentryEnabled = !!ENV.SENTRY_DSN;

type LogContext = Record<string, unknown>;

/**
 * 前端日誌工具
 *
 * 用途：依環境分層處理日誌輸出，隔離 console 與監控服務之間的耦合。
 *
 * - warn：預期的業務邏輯錯誤，dev 環境顯示完整資訊，production 靜音。
 * - error：非預期系統錯誤，dev 顯示完整資訊；只要 DSN 已設定就送往 Sentry。
 */
export const logger = {
  warn: (message: string, context?: LogContext): void => {
    if (isDev) {
      console.warn('[WARN]', message, context);
    }
  },

  error: (message: string, errorOrContext?: Error | LogContext): void => {
    // console 輸出依環境決定詳細程度
    if (isDev) {
      console.error('[ERROR]', message, errorOrContext);
    } else {
      console.error('[ERROR]', message);
    }

    // Sentry 捕捉：只要 DSN 已設定就送，不限環境
    if (isSentryEnabled) {
      if (errorOrContext instanceof Error) {
        Sentry.captureException(errorOrContext);
      } else {
        Sentry.captureMessage(message, { level: 'error', extra: errorOrContext });
      }
    }
  },
};
