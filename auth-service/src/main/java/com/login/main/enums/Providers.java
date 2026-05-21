package com.login.main.enums;

/**
 * 定義使用者登入的來源提供者
 */
public enum Providers {
    /**
     * 本地資料庫登入 (帳號密碼)
     */
    LOCAL,

    /**
     * Google 登入
     */
    GOOGLE,
    
    /**
     * Discord 登入
     */
    DISCORD,

    /**
     * Line 登入
     */
    LINE;

    public String getProviderName() {
        return name();
    }
}
