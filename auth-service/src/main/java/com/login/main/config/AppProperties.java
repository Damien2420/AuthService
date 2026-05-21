package com.login.main.config;

import java.util.Arrays;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 集中管理專案自定義配置的屬性類別
 * 使用 @ConfigurationProperties(prefix = "app") 將 application.properties 中以 app 開頭的屬性自動對應
 * 例如：app.frontend-url 會自動注入到 frontendUrl 欄位中
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 前端應用的 URL 位址
     */
    private String frontendUrl;

    /**
     * 郵件相關設定
     */
    private Mail mail = new Mail();

    /**
     * Cookie 相關設定
     */
    private Cookie cookie = new Cookie();

    /**
     * 郵件設定內部類別
     */
    @Data
    public static class Mail {

        /**
         * 郵件寄件人地址
         */
        private String from;
    }

    /**
     * Cookie 設定內部類別
     */
    @Data
    public static class Cookie {

        /**
         * 是否啟用 Secure 屬性
         */
        private boolean secure = false;
    }

    /**
     * Cloudflare Turnstile 相關設定
     */
    private Turnstile turnstile = new Turnstile();

    /**
     * Turnstile 設定內部類別
     */
    @Data
    public static class Turnstile {

        /**
         * 是否啟用 Turnstile 驗證
         */
        private boolean enabled = true;

        /**
         * Cloudflare Turnstile Secret Key
         */
        private String secretKey;
    }

    /**
     * Sentry Tunnel 相關設定
     */
    private Sentry sentry = new Sentry();

    /**
     * Sentry Tunnel 設定內部類別
     */
    @Data
    public static class Sentry {

        /**
         * 允許轉發的 Sentry Project ID 白名單，逗號分隔字串
         */
        private String allowedProjectIds = "";

        /**
         * 檢查指定的 projectId 是否在白名單內
         *
         * @param projectId 從 DSN 路徑取得的數字 ID
         * @return 白名單包含該 ID 時回傳 true；白名單為空或不包含時回傳 false
         */
        public boolean isProjectIdAllowed(String projectId) {
            if (allowedProjectIds == null || allowedProjectIds.isBlank()) return false;
            return Arrays.stream(allowedProjectIds.split(","))
                         .map(String::trim)
                         .anyMatch(id -> id.equals(projectId));
        }
    }

}
