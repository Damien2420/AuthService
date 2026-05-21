import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { useLocation,useNavigate } from "react-router-dom";

import { Spinner } from "@/components/ui/spinner";
import type { AuthContextValue,AuthState } from "@/types/auth";
import apiClient from "@/utils/api-util";

/**
 * AuthContext
 *
 * 管理應用程式的認證狀態，包含 accessToken 和 username。
 * Token 存於 React memory state 而非 localStorage，以防止 XSS 攻擊。
 */
const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * AuthProvider 組件
 *
 * 提供認證狀態管理和自動初始化功能：
 * 1. 管理 accessToken 和 username 的 state
 * 2. 初始化時自動呼叫 /api/v1/auth/refresh 嘗試恢復登入狀態
 * 3. 注入 token getter/updater 到 api-util
 * 4. 提供 setAuth/clearAuth/updateToken API
 *
 * @param children - 子組件
 */
export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [authState, setAuthState] = useState<AuthState>({
    accessToken: null,
    username: null,
    isInitialized: false,
    isLoading: true,
  });

  const navigate = useNavigate();
  const location = useLocation();

  // 防止雙執行
  const hasInitialized = useRef(false);

  // 快照 mount 時的初始路徑，避免將 location.pathname 放入 useEffect 依賴陣列
  const initialPathname = useRef(location.pathname);

  /**
   * 設定認證資訊
   */
  const setAuth = useCallback((token: string, username: string) => {
    setAuthState({
      accessToken: token,
      username,
      isInitialized: true,
      isLoading: false,
    });
  }, []);

  /**
   * 清除認證資訊
   */
  const clearAuth = useCallback(() => {
    setAuthState({
      accessToken: null,
      username: null,
      isInitialized: true,
      isLoading: false,
    });
  }, []);

  /**
   * 更新 Token（供 api-util refresh interceptor 使用）
   */
  const updateToken = useCallback((token: string) => {
    setAuthState((prev) => ({ ...prev, accessToken: token }));
  }, []);

  // 注入 Token Getter 到 api-util（當 token 變化時更新）
  useEffect(() => {
    apiClient.setTokenGetter(() => authState.accessToken);
  }, [authState.accessToken]);

  // 注入 Token Updater 和 ClearAuth Callback 到 api-util（只需註冊一次）
  useEffect(() => {
    apiClient.setTokenUpdater(updateToken);
    apiClient.setClearAuthCallback(() => {
      clearAuth();
      navigate("/", { replace: true });
    });
  }, [updateToken, clearAuth, navigate]);

  // 初始化邏輯：嘗試透過 refresh token 恢復登入狀態
  useEffect(() => {
    // 防止 StrictMode 雙執行
    if (hasInitialized.current) return;
    hasInitialized.current = true;

    // 如果 mount 時就在認證相關頁面，不執行自動 refresh
    const authPaths = ["/", "/login", "/auth/callback", "/reset-password", "/verify-email"];
    if (authPaths.includes(initialPathname.current)) {
      setAuthState((prev) => ({
        ...prev,
        isInitialized: true,
        isLoading: false,
      }));
      return;
    }

    const initAuth = async () => {
      try {
        // 嘗試透過 HttpOnly Cookie 中的 refreshToken 恢復登入狀態
        const response = await apiClient.refresh();

        if (response.success === true && response.data) {
          const { accessToken, username } = response.data;
          setAuthState({
            accessToken,
            username,
            isInitialized: true,
            isLoading: false,
          });
        } else {
          throw new Error("Refresh failed");
        }
      } catch {
        // Refresh 失敗，清除狀態並導向首頁
        console.log("[Auth] No valid session, redirecting to home");
        setAuthState({
          accessToken: null,
          username: null,
          isInitialized: true,
          isLoading: false,
        });
        navigate("/", { replace: true });
      }
    };

    initAuth();

    // cleanup 時重置 flag，讓第二次 mount 能正確執行初始化
    return () => {
      hasInitialized.current = false;
    };
  }, [navigate]);

  const value: AuthContextValue = {
    ...authState,
    setAuth,
    clearAuth,
    updateToken,
  };

  // 初始化未完成時顯示 Loading
  if (!authState.isInitialized) {
    return (
      <div className="fixed inset-0 flex items-center justify-center bg-background">
        <Spinner className="size-10" />
      </div>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

/**
 * useAuth Hook
 *
 * 提供存取 AuthContext 的便捷方式。
 * 必須在 AuthProvider 內使用，否則會拋出錯誤。
 *
 * @returns AuthContextValue - 包含認證狀態和操作方法
 * @throws Error - 如果在 AuthProvider 外部使用
 */
// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
};