package com.login.main.service;

/**
 * 帳號登入嘗試追蹤服務介面
 *
 * 透過 Redis 記錄登入失敗次數，實現帳號鎖定機制。
 * 當同一帳號連續登入失敗達門檻次數後，暫時鎖定該帳號防止暴力攻擊。
 */
public interface LoginAttemptService {

    /**
     * 記錄一次登入失敗
     *
     * 累計指定帳號的失敗次數。第一次失敗時啟動固定視窗計時，
     * 視窗內累積達門檻即進入鎖定狀態。
     *
     * @param username 登入失敗的使用者名稱
     */
    void recordFailedAttempt(String username);

    /**
     * 判斷帳號是否處於鎖定狀態
     *
     * @param username 欲查詢的使用者名稱
     * @return true 表示帳號已鎖定（失敗次數 >= 門檻），false 表示可正常登入
     */
    boolean isLocked(String username);

    /**
     * 清除帳號的登入失敗記錄
     *
     * 在使用者成功登入後呼叫，重置失敗計數，避免正常使用者因歷史失敗累積而被鎖定。
     *
     * @param username 登入成功的使用者名稱
     */
    void clearAttempts(String username);
}
