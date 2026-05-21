package com.login.main.service;

import com.login.main.common.result.Result;

/**
 * Cloudflare Turnstile 人機驗證服務介面
 *
 * 封裝與 Cloudflare Turnstile siteverify API 的互動邏輯，
 * 驗證前端傳入的 Turnstile Token 是否合法，以防止自動化機器人攻擊。
 */
public interface TurnstileService {

    /**
     * 驗證 Cloudflare Turnstile Token
     *
     * 呼叫 Cloudflare siteverify API 驗證前端取得的 Token。
     * 若 {@code app.turnstile.enabled=false}，直接略過驗證回傳成功（用於本地開發）。
     *
     * @param token 前端 Turnstile Widget 產生的驗證 Token
     * @return 驗證通過回傳 {@code Result.success(null)}；
     *         驗證失敗回傳 {@code Result.fail(CAPTCHA_FAILED)}
     */
    Result<Void> verify(String token);
}
