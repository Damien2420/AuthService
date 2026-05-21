/**
 * 認證狀態
 *
 * 定義 AuthContext 中管理的狀態結構
 */
export interface AuthState {
  /** Access Token (存於 React memory state) */
  accessToken: string | null;
  /** 使用者名稱 */
  username: string | null;
  /** 初始化狀態 - 表示是否已完成首次 refresh 嘗試 */
  isInitialized: boolean;
  /** 是否正在載入（進行認證相關操作中） */
  isLoading: boolean;
}

/**
 * AuthContext 提供的 API
 *
 * 繼承 AuthState 的所有狀態，並新增認證操作方法
 */
export interface AuthContextValue extends AuthState {
  /**
   * 設定認證資訊
   *
   * 登入成功後呼叫，同時設定 token 和 username
   *
   * @param token - Access Token
   * @param username - 使用者名稱
   */
  setAuth: (token: string, username: string) => void;

  /**
   * 清除認證資訊
   *
   * 登出或 session 過期時呼叫
   */
  clearAuth: () => void;

  /**
   * 更新 Token
   *
   * 供 api-util 的 refresh interceptor 使用，
   * 當 refresh API 成功時更新 Context 中的 token
   *
   * @param token - 新的 Access Token
   */
  updateToken: (token: string) => void;
}