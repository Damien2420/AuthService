import type { AxiosInstance, AxiosRequestConfig, AxiosRequestHeaders, InternalAxiosRequestConfig } from "axios";
import axios from "axios";
import { v4 as uuidv4 } from "uuid";

import { ENV } from "../config/env";
import { API_ENDPOINTS } from "../constants/api-paths";
import { ApiError } from "../types/api-error";
import { logger } from "./logger";

/**
 * 認證回應資料結構
 */
export interface AuthResponse {
  accessToken: string;
  username: string;
}

/**
 * 註冊請求資料結構
 */
export interface RegisterRequest {
  email: string;
  username: string;
  password: string;
  verificationCode: string;
  turnstileToken?: string;
}

/**
 * 基礎回應結構 (與後端 CustomApiResponse 對齊)
 */
interface BaseResponse<T = unknown> {
  success: boolean;
  code: number;
  errorKey?: string;
  requestId?: string;
  message?: string;
  data: T;
  timestamp?: string;
}

/**
 * 擴充 AxiosRequestConfig 以支援自定義屬性
 */
interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  headers: AxiosRequestHeaders; // 確保 headers 屬性存在
}

/**
 * API 客戶端服務
 * 
 * 封裝所有與後端通訊的 Axios 請求。
 * 集中管理認證標頭注入、自動錯誤處理以及響應資料型別轉換。
 */
class ApiClient {
  private instance: AxiosInstance;
  private isRefreshing = false;
  private refreshSubscribers: ((token: string) => void)[] = [];

  // Token 管理函數（由 AuthContext 注入）
  private getAccessToken: (() => string | null) | null = null;
  private tokenUpdater: ((token: string) => void) | null = null;
  private clearAuthCallback: (() => void) | null = null;

  /**
   * 注入 Token Getter
   *
   * AuthContext 初始化時呼叫，讓 interceptor 能動態取得最新的 token
   *
   * @param getter - 取得 token 的函數
   */
  public setTokenGetter(getter: () => string | null): void {
    this.getAccessToken = getter;
  }

  /**
   * 注入 Token Updater
   *
   * 當 refresh API 成功時，呼叫此函數更新 AuthContext 中的 token
   *
   * @param updater - 更新 token 的回調函數
   */
  public setTokenUpdater(updater: (token: string) => void): void {
    this.tokenUpdater = updater;
  }

  /**
   * 注入清除認證回調
   *
   * 當 refresh 失敗時，呼叫此函數清除認證狀態並導向登入頁
   *
   * @param callback - 清除認證的回調函數
   */
  public setClearAuthCallback(callback: () => void): void {
    this.clearAuthCallback = callback;
  }

  constructor() {
    this.instance = axios.create({
      baseURL: ENV.API_BASE_URL,
      withCredentials: true, // 支援跨域 Cookie (用於 Refresh Token)
      headers: {
        "Content-Type": "application/json",
      },
    });

    this.initializeInterceptors();
  }

  /**
   * 初始化攔截器
   */
  private initializeInterceptors() {
    // 自動注入 Trace ID 與 Access Token
    this.instance.interceptors.request.use(
      (config) => {
        // [TRACE-ID] 前端生成全鏈路追蹤代碼
        if (config.headers) {
            config.headers['X-Request-ID'] = uuidv4();
        }

        // 如果是登入、註冊、刷新等路徑，不注入 Token
        if (config.url?.includes("/auth/")) {
          return config;
        }

        // 從 AuthContext 注入的 getter 動態取得 token
        const token = this.getAccessToken?.();
        if (token && config.headers) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    // Response Interceptor: 處理 401 並自動刷新 Token
    this.instance.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config as CustomAxiosRequestConfig;

        // 如果是 401 錯誤且「不是」在執行 auth 相關請求，才嘗試刷新
        if (
          error.response?.status === 401 && 
          !originalRequest._retry && 
          !originalRequest.url?.includes("/auth/")
        ) {
          if (this.isRefreshing) {
            // 如果正在刷新中，將請求放入隊列
            return new Promise((resolve) => {
              this.subscribeTokenRefresh((token) => {
                if (originalRequest.headers) {
                  originalRequest.headers.Authorization = `Bearer ${token}`;
                }
                resolve(this.instance(originalRequest));
              });
            });
          }

          originalRequest._retry = true;
          this.isRefreshing = true;

          try {
            // 呼叫後端刷新介面 (通常後端會從 HttpOnly Cookie 讀取 Refresh Token)
            const res = await axios.post<BaseResponse<AuthResponse>>(
              `${this.instance.defaults.baseURL}${API_ENDPOINTS.AUTH.REFRESH}`,
              {},
              { withCredentials: true, headers: { 'X-Request-ID': uuidv4() } }
            );

            const newAccessToken = res.data.data.accessToken;

            // 透過注入的 updater 同步 token 到 AuthContext
            this.tokenUpdater?.(newAccessToken);

            this.onTokenRefreshed(newAccessToken);
            this.isRefreshing = false;

            // 用新的 Token 重試原始請求
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
            }
            return this.instance(originalRequest);
          } catch {
            this.isRefreshing = false;
            // 清空排隊中的請求，避免 Promise 永遠 pending 造成記憶體洩漏
            this.refreshSubscribers = [];
            // 刷新失敗，透過注入的回調清除認證狀態並導向登入
            this.clearAuthCallback?.();
            return Promise.reject(new Error("登入已過期，請重新登入"));
          }
        }

        const status = error.response?.status ?? 0;
        const errorKey = error.response?.data?.errorKey ?? 'UNKNOWN_ERROR';
        const traceId =
          error.response?.headers?.['x-request-id'] ??
          error.response?.data?.requestId;
        const url = error.config?.url;
        const method = error.config?.method?.toUpperCase();

        const logContext = { url, method, status, errorKey, traceId };

        // 5xx 或網路中斷（status=0）為非預期系統異常，須送監控告警
        // 4xx 為預期業務邏輯錯誤，僅 dev 環境記錄
        if (status >= 500 || status === 0) {
          logger.error(`[API] ${status} ${errorKey}`, logContext);
        } else {
          logger.warn(`[API] ${status} ${errorKey}`, logContext);
        }

        return Promise.reject(new ApiError({
          status,
          errorKey,
          traceId,
          url,
          method,
          message: error.response?.data?.message,
        }));
      }
    );
  }

  private subscribeTokenRefresh(cb: (token: string) => void) {
    this.refreshSubscribers.push(cb);
  }

  private onTokenRefreshed(token: string) {
    this.refreshSubscribers.forEach((cb) => cb(token));
    this.refreshSubscribers = [];
  }

  /**
   * 登入方法
   *
   * 呼叫後端登入 API，成功後由呼叫端（Login.tsx）透過 AuthContext 設定 token
   *
   * @param data - 登入請求資料（username, password, rememberMe）
   * @returns 包含 accessToken 和 username 的認證回應
   */
  public async login(data: unknown): Promise<BaseResponse<AuthResponse>> {
    const response = await this.instance.post<BaseResponse<AuthResponse>>(API_ENDPOINTS.AUTH.LOGIN, data);
    return response.data;
  }

  /**
   * OAuth2 一次性授權碼換取 Token
   *
   * 接受後端 OAuth2 Callback redirect 中的一次性 code，向後端換取 JWT Token。
   * RefreshToken 由後端透過 HttpOnly Cookie 自動設定，無需前端處理。
   * 成功後由呼叫端（OAuthCallback.tsx）透過 AuthContext 設定 token。
   *
   * @param code - OAuth2 一次性授權碼
   * @returns 包含 accessToken 和 username 的認證回應
   */
  public async exchangeOAuth2Code(code: string): Promise<BaseResponse<AuthResponse>> {
    const response = await this.instance.post<BaseResponse<AuthResponse>>(
      API_ENDPOINTS.AUTH.OAUTH2_TOKEN,
      { code }
    );
    return response.data;
  }

  /**
   * 刷新 Token
   *
   * 供 AuthContext 初始化時使用，嘗試透過 HttpOnly Cookie 中的 refreshToken 恢復登入狀態。
   * 注意：此方法直接使用 axios.post 而非 this.instance，避免 interceptor 干擾。
   *
   * @returns 包含 accessToken 和 username 的認證回應
   */
  public async refresh(): Promise<BaseResponse<AuthResponse>> {
    const response = await axios.post<BaseResponse<AuthResponse>>(
      `${this.instance.defaults.baseURL}${API_ENDPOINTS.AUTH.REFRESH}`,
      {},
      { withCredentials: true, headers: { 'X-Request-ID': uuidv4() } }
    );
    return response.data;
  }

  /**
   * 登出方法
   *
   * 呼叫後端登出 API，將 AccessToken 與 RefreshToken 加入黑名單，並清除 HttpOnly Cookie。
   * 由於攔截器對 /auth/* 路徑跳過 token 注入，此處手動附加 Authorization Header，
   * 確保後端能取得 AccessToken 並將其加入黑名單。
   *
   * @returns 登出結果（永遠成功，即使後端 Redis 異常）
   */
  public async logout(): Promise<BaseResponse<null>> {
    const token = this.getAccessToken?.();
    const config: AxiosRequestConfig = {};
    if (token) {
      config.headers = { Authorization: `Bearer ${token}` };
    }
    const response = await this.instance.post<BaseResponse<null>>(API_ENDPOINTS.AUTH.LOGOUT, undefined, config);
    return response.data;
  }

  /**
   * 註冊方法
   *
   * 呼叫後端註冊 API，成功後由呼叫端（Register.tsx）透過 AuthContext 設定 token。
   *
   * @param data - 註冊請求資料（email, username, password, verificationCode, turnstileToken）
   * @returns 包含 accessToken 和 username 的認證回應
   */
  public async register(data: RegisterRequest): Promise<BaseResponse<AuthResponse>> {
    const response = await this.instance.post<BaseResponse<AuthResponse>>(
      API_ENDPOINTS.AUTH.REGISTER,
      data
    );
    return response.data;
  }

  /**
   * 發送 Email 驗證碼
   *
   * 在使用者填入 Email 後觸發，生成 6 位數 OTP 並寄至指定信箱。
   * 若 Email 已被使用，後端回傳 EMAIL_ALREADY_EXISTS。
   *
   * @param email - 使用者填入的電子郵件地址
   * @returns 成功回傳 200，Email 已存在回傳 EMAIL_ALREADY_EXISTS
   */
  public async sendEmailVerification(email: string): Promise<BaseResponse<null>> {
    const response = await this.instance.post<BaseResponse<null>>(
      API_ENDPOINTS.AUTH.EMAIL.SEND_VERIFICATION,
      { email }
    );
    return response.data;
  }

  /**
   * 重新寄出 Email 驗證連結
   *
   * 針對已註冊但尚未完成 Email 驗證的使用者，觸發後端寄出含驗證連結的信件。
   *
   * @param username - 使用者帳號名稱
   * @returns 成功回傳 200，帳號已驗證回傳 EMAIL_ALREADY_VERIFIED，帳號不存在回傳 USER_NOT_FOUND
   */
  public async resendVerification(username: string): Promise<BaseResponse<null>> {
    const response = await this.instance.post<BaseResponse<null>>(
      API_ENDPOINTS.AUTH.EMAIL.RESEND_VERIFICATION,
      { username }
    );
    return response.data;
  }

  /**
   * 驗證 Email 連結 token
   *
   * 使用者點擊驗證連結後，前端取出 URL 中的 token 送往後端完成帳號 Email 驗證。
   *
   * @param token - 驗證連結中的 UUID token
   * @returns 成功回傳 200，token 無效或過期回傳 VERIFICATION_LINK_INVALID
   */
  public async verifyEmail(token: string): Promise<BaseResponse<null>> {
    const response = await this.instance.post<BaseResponse<null>>(
      API_ENDPOINTS.AUTH.EMAIL.VERIFY,
      { token }
    );
    return response.data;
  }

  /**
   * 忘記密碼 - 發送 OTP 驗證碼
   *
   * 驗證 Email 帳號存在後，觸發後端發送 6 位數 OTP 至信箱。
   *
   * @param email - 使用者電子郵件
   * @returns 成功回傳 200，帳號不存在回傳 USER_NOT_FOUND
   */
  public async forgotPassword(email: string): Promise<BaseResponse<null>> {
    const response = await this.instance.post<BaseResponse<null>>(
      API_ENDPOINTS.AUTH.PASSWORD.FORGOT,
      { email }
    );
    return response.data;
  }

  /**
   * 忘記密碼 - 驗證 OTP
   *
   * 驗證使用者輸入的 6 位數 OTP，成功後取得一次性 Reset Token（有效期 10 分鐘）。
   *
   * @param email - 使用者電子郵件
   * @param otp   - 6 位數一次性驗證碼
   * @returns 成功回傳含 resetToken 的資料，失敗回傳 OTP_INVALID
   */
  public async verifyOtp(email: string, otp: string): Promise<BaseResponse<{ resetToken: string }>> {
    const response = await this.instance.post<BaseResponse<{ resetToken: string }>>(
      API_ENDPOINTS.AUTH.PASSWORD.VERIFY_OTP,
      { email, otp }
    );
    return response.data;
  }

  /**
   * 忘記密碼 - 重設密碼
   *
   * 使用 Reset Token 完成密碼重設。成功後，使用者所有現有的 Session 將立即失效。
   *
   * @param resetToken  - 由 verifyOtp 取得的一次性重設權杖
   * @param newPassword - 新密碼（6-20 位英數字元）
   * @returns 成功回傳 200，Token 失效回傳 RESET_TOKEN_INVALID
   */
  public async resetPassword(resetToken: string, newPassword: string): Promise<BaseResponse<null>> {
    const response = await this.instance.post<BaseResponse<null>>(
      API_ENDPOINTS.AUTH.PASSWORD.RESET,
      { resetToken, newPassword }
    );
    return response.data;
  }

  /**
   * GIS One Tap 登入
   *
   * 將 Google Identity Services 回傳的 ID Token 送至後端驗證。
   * 後端驗證成功後，自動建立或查詢帳號並回傳 JWT Token。
   * RefreshToken 由後端透過 HttpOnly Cookie 自動設定。
   *
   * @param token GIS 回傳的 Google ID Token
   * @returns 包含 accessToken 和 username 的認證回應
   */
  public async googleLogin(token: string): Promise<BaseResponse<AuthResponse>> {
    const response = await this.instance.post<BaseResponse<AuthResponse>>(
      API_ENDPOINTS.AUTH.GOOGLE_LOGIN,
      { token }
    );
    return response.data;
  }

  /**
   * 通用請求方法
   */
  public async request<T = unknown>(config: AxiosRequestConfig): Promise<BaseResponse<T>> {
    const response = await this.instance.request<BaseResponse<T>>(config);
    return response.data;
  }

  public async get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<BaseResponse<T>> {
    return this.request<T>({ ...config, method: "GET", url });
  }

  public async post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<BaseResponse<T>> {
    return this.request<T>({ ...config, method: "POST", url, data });
  }
}

export default new ApiClient();
