import axios from "axios";

import { DEFAULT_ERROR, ERROR_MAPPING, type ErrorDetail } from "@/constants/error-mapping";
import { ApiError } from "@/types/api-error";

/**
 * API 異常轉譯器
 *
 * 將錯誤物件解析並映射為 ErrorDetail 結構。
 * 優先處理由 interceptor 轉換的 ApiError，向下相容直接使用 axios（不經 interceptor）的路徑。
 *
 * @param error - 捕獲到的錯誤物件（ApiError、AxiosError 或其他）
 * @returns 格式化後的錯誤詳情，包含 userMessage、actionMsg、severity 等
 */
export const handleApiError = (error: unknown): ErrorDetail => {
    if (ApiError.isApiError(error)) {
        const mappedError = ERROR_MAPPING[error.errorKey];
        const baseError = mappedError ?? {
            ...DEFAULT_ERROR,
            debugCode: `UNK_${error.errorKey}`,
        };
        return { ...baseError, traceId: error.traceId };
    }

    if (axios.isAxiosError(error)) {
        const response = error.response;
        const responseData = (response?.data ?? {}) as {
            errorKey?: string;
            requestId?: string;
        };

        const traceId = response?.headers?.['x-request-id'] || responseData?.requestId;
        const errorKey = responseData?.errorKey || 'UNKNOWN';

        const mappedError = ERROR_MAPPING[errorKey];
        const baseError = mappedError ?? {
            ...DEFAULT_ERROR,
            debugCode: `UNK_${errorKey}`,
        };

        return { ...baseError, traceId };
    }

    return {
        ...DEFAULT_ERROR,
        debugCode: 'NON_AXIOS_ERROR',
    };
};
