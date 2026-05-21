/**
 * API 錯誤領域物件
 *
 * 用途：由 api-util.ts response interceptor 建立，取代原始 Axios error 向上傳遞。
 *       攜帶完整診斷資訊供 logger 記錄與 api-error-handler 轉換為 UI 訊息使用。
 * 接收：HTTP 狀態碼、後端 errorKey、全鏈路追蹤 ID、請求 URL 與方法。
 * 回傳：語意化的 domain error 實例。
 */
export class ApiError extends Error {
  public readonly status: number;
  public readonly errorKey: string;
  public readonly traceId?: string;
  public readonly url?: string;
  public readonly method?: string;

  constructor(params: {
    status: number;
    errorKey: string;
    traceId?: string;
    url?: string;
    method?: string;
    message?: string;
  }) {
    super(params.message ?? params.errorKey);
    this.name = 'ApiError';
    this.status = params.status;
    this.errorKey = params.errorKey;
    this.traceId = params.traceId;
    this.url = params.url;
    this.method = params.method;
  }

  /**
   * TypeScript type guard
   *
   * @param error - 任意 unknown 值
   * @returns 是否為 ApiError 實例
   */
  static isApiError(error: unknown): error is ApiError {
    return error instanceof ApiError;
  }
}