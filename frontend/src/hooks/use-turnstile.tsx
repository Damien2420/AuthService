import { useEffect, useRef, useState } from "react";

import { ENV } from "@/config/env";

interface UseTurnstileProps {
  onError: () => void;
}

const useTurnstile = ( { onError }: UseTurnstileProps) => {
  const [isTurnstileReady, setIsTurnstileReady] = useState(!ENV.TURNSTILE_SITE_KEY);
  const widgetIdRef = useRef<string | null>(null);
  const turnstileRef = useRef<HTMLDivElement>(null);
  const turnstileTokenRef = useRef<string | null>(null);

  const onErrorPropsRef = useRef(onError);

  // 每次 render 後更新 ref，確保 widget 的 error-callback永遠是最新的
  useEffect(() => {
    onErrorPropsRef.current = onError;
  });

  useEffect(() => {
      if (!ENV.TURNSTILE_SITE_KEY) return;
  
      const renderWidget = () => {
        // 如果 DOM 掛載點不存在，或 Widget 已經渲染過（有 widgetId），就不重複渲染
        if (!turnstileRef.current || widgetIdRef.current) return;
        widgetIdRef.current = turnstile.render(turnstileRef.current, {
          sitekey: ENV.TURNSTILE_SITE_KEY,
          theme: 'auto',
          callback: (token: string) => {
            turnstileTokenRef.current = token;
            setIsTurnstileReady(true);
          },
          'expired-callback': () => {
            turnstileTokenRef.current = null;
            setIsTurnstileReady(false);
          },
          'error-callback': () => {
            turnstileTokenRef.current = null;
            setIsTurnstileReady(false);
            onErrorPropsRef.current();
          }
        }) ?? null;
      };
  
      // 透過 setInterval 檢查 turnstile 是否已存在的計時器
      let pollInterval: ReturnType<typeof setInterval> | null = null;
  
      // 先檢查 turnstile 是否已存在，Turnstile 是透過 <script> CDN 非同步載入的，script 可能還沒讀取完成
      if (typeof turnstile !== 'undefined') {
        renderWidget();
      } else {
        // 如果 turnstile 不存在，就每 50ms 檢查一次，直到 turnstile 存在
        pollInterval = setInterval(() => {
          if (typeof turnstile !== 'undefined') {
            clearInterval(pollInterval!);
            pollInterval = null;
            renderWidget();
          }
        }, 50);
      }
  
      return () => {
        if (pollInterval) clearInterval(pollInterval);
        if (widgetIdRef.current) {
          turnstile.remove(widgetIdRef.current);
          widgetIdRef.current = null;
          turnstileTokenRef.current = null;
        }
      };
    }, []);
    
    const resetTurnstileWidgetUponError = (): void => {
      if (widgetIdRef.current) {
        turnstile.reset(widgetIdRef.current);
        turnstileTokenRef.current = null;
        setIsTurnstileReady(false);
      }
    };

    return {
      isTurnstileReady,
      turnstileRef,
      resetTurnstileWidgetUponError,
      turnstileTokenRef,
    }
}

export default useTurnstile;