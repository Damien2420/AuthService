import * as Sentry from '@sentry/react';
import React from 'react';
import {
    createRoutesFromChildren,
    matchRoutes,
    useLocation,
    useNavigationType,
} from 'react-router-dom';

/**
 * Sentry 初始化
 *
 * 僅在設定 VITE_SENTRY_DSN 環境變數時啟動，未設定則靜默跳過。
 */
if (import.meta.env.VITE_SENTRY_DSN) {
    Sentry.init({
        dsn: import.meta.env.VITE_SENTRY_DSN as string,
        tunnel: import.meta.env.VITE_API_BASE_URL + '/api/v1/monitoring/tunnel',

        environment: import.meta.env.MODE,

        integrations: [
            Sentry.reactRouterV7BrowserTracingIntegration({
                useEffect: React.useEffect,
                useLocation,
                useNavigationType,
                createRoutesFromChildren,
                matchRoutes,
            }),

        ],

        // Dev 環境開啟 debug log，可在 browser console 看到 Sentry 的送出紀錄
        debug: import.meta.env.DEV,

        sendDefaultPii: false,
    });
}