package com.login.main.common.result;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Function;

import com.login.main.common.error.ErrorCode;

/**
 * 標準化操作結果容器
 * 
 * 採用泛型封裝業務邏輯的執行結果，包含成功狀態、錯誤訊息集以及回傳資料。
 * 提供函數式編程 API (bind, map, tap) 以優化代碼鏈結。
 * 
 * @param <T> 裝載資料的型別
 */
public class Result<T> implements Serializable {

    private boolean isSuccess;
    private String[] errorMessages;
    private T data;
    private ErrorCode errorCode;

    protected Result() {}

    protected Result(boolean isSuccess, String[] errorMessages, T data) {
        this.isSuccess = isSuccess;
        this.errorMessages = errorMessages;
        this.data = data;
    }

    protected Result(boolean isSuccess, String[] errorMessages, T data, ErrorCode errorCode) {
        this.isSuccess = isSuccess;
        this.errorMessages = errorMessages;
        this.data = data;
        this.errorCode = errorCode;
    }

    // --- Success ---
    /**
     * 建立成功結果 (空資料)
     * 
     * 表示操作已成功執行，但不需回傳任何具體資料。
     * @param <T> 目標型別
     * @return 成功的 Result 實體
     */
    public static <T> Result<T> success() {
        return new Result<>(true, null, null);
    }

    /**
     * 建立成功結果 (含資料)
     * 
     * 操作成功並將結果物件傳回呼叫端。
     * @param data 回傳資料
     * @param <T> 資料型別
     * @return 成功的 Result 實體
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(true, null, data);
    }

    // --- Fail ---
    /**
     * 建立失敗結果 (單一訊息)
     * 
     * 針對單一已知錯誤建立失敗回應。
     * @param message 錯誤描述
     * @param <T> 目標型別
     * @return 失敗的 Result 實體
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(false, new String[]{message}, null);
    }

    /**
     * 建立失敗結果 (訊息陣列)
     * 
     * 針對多個錯誤 (例如多個參數驗證失敗) 建立失敗回應。
     * @param messages 錯誤描述集
     * @param <T> 目標型別
     * @return 失敗的 Result 實體
     */
    public static <T> Result<T> fail(String[] messages) {
        return new Result<>(false, messages, null);
    }

    /**
     * 建立失敗結果 (自定義錯誤碼)
     * 
     * 根據系統預定義的 ErrorCode 枚舉建立失敗回應。
     * @param errorCode 錯誤碼物件
     * @param <T> 目標型別
     * @return 失敗的 Result 實體
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(false, new String[]{errorCode.getMessage()}, null, errorCode);
    }

    // --- Getters ---
    public boolean isSuccess() { return isSuccess; }
    public boolean isFailed() { return !isSuccess; }
    public String[] getErrorMessages() { return errorMessages; }
    public T getData() { return data; }
    public ErrorCode getErrorCode() { return errorCode; }

    // --- Functional Methods ---

    /**
     * 如果目前是成功，則執行下一個會回傳 Result 的操作。
     * 如果目前是失敗，則直接傳回目前的失敗，不再執行操作。
     */
    public <R> Result<R> bind(Function<T, Result<R>> mapper) {
        if (this.isFailed()) {
            return new Result<>(false, this.errorMessages, null, this.errorCode);
        }

        return mapper.apply(this.data);
    }

    /**
     * 成功時轉換資料：Map
     * 如果目前是成功，則將資料轉換為另一種型別。
     */
    public <R> Result<R> map(Function<T, R> mapper) {
        if (this.isFailed()) {
            return new Result<>(false, this.errorMessages, null, this.errorCode);
        }

        return Result.success(mapper.apply(this.data));
    }

    /**
     * 成功時執行副作用：Tap
     * 如果目前是成功，則執行一個不回傳結果的操作（例如日誌、修改狀態等）。
     */
    public Result<T> tap(Consumer<T> consumer) {
        if (this.isSuccess()) {
            consumer.accept(this.data);
        }

        return this;
    }

    /**
     * 成功時執行副作用：onSuccess
     * @param action 執行的動作
     * @return 原物件
     */
    public Result<T> onSuccess(Consumer<T> action) {
        if (this.isSuccess()) {
            action.accept(this.data);
        }
        return this;
    }

    /**
     * 失敗時執行副作用：onFailure (常用於記錄錯誤日誌)
     * @param action 執行的動作 (接收錯誤訊息陣列)
     * @return 原物件
     */
    public Result<T> onFailure(Consumer<String[]> action) {
        if (this.isFailed()) {
            action.accept(this.errorMessages);
        }
        return this;
    }

    /**
     * 終結方法：若為失敗狀態，使用提供的工廠函式拋出例外；若為成功，回傳資料。
     * 作為 ROP 管道的出口，將 Result 橋接至例外機制。
     * 呼叫端範例：result.orThrow(AppException::new)
     *
     * @param exceptionMapper 接受 ErrorCode，回傳 RuntimeException 子類的工廠函式
     * @param <E> 例外類型，必須繼承 RuntimeException
     * @return 成功時回傳資料
     * @throws E 失敗時使用 errorCode 建立並拋出例外
     */
    public <E extends RuntimeException> T orThrow(Function<ErrorCode, E> exceptionMapper) {
        if (this.isFailed()) {
            throw exceptionMapper.apply(this.errorCode);
        }
        return this.data;
    }
}
