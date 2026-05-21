package com.login.main.filter;

import com.login.main.common.error.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * IP 級別請求頻率限制過濾器
 *
 * 使用 Bucket4j Token Bucket 演算法搭配 Redis 分散式儲存，
 * 對 /login、/register、/refresh、/oauth2/token 端點依來源 IP 進行請求頻率限制。
 * 超過限制時回傳 HTTP 429 並附帶 Retry-After 標頭。
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisConnectionFactory redisConnectionFactory;
    private final HandlerExceptionResolver handlerExceptionResolver;

    /**
     * 登入端點每分鐘允許的最大請求數。
     * 必須高於帳號鎖定閾值（5 次），確保帳號鎖定機制能在 rate limit 之前觸發。
     */
    @Value("${app.rate-limiting.login-capacity:20}")
    private int loginCapacity;

    /** 用於在 @PreDestroy 關閉時釋放連線 */
    private StatefulRedisConnection<String, byte[]> lettuceConnection;

    /** Bucket4j Redis Proxy Manager，用於跨節點共享 bucket 狀態 */
    private LettuceBasedProxyManager<String> proxyManager;

    /** 受限制的端點集合 */
    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/oauth2/token",
            "/api/v1/auth/email/send-verification",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/verify-otp",
            "/api/v1/auth/password/reset"
    );

    /** 各端點對應的 BucketConfiguration，於 @PostConstruct 初始化以套用注入的設定值 */
    private Map<String, BucketConfiguration> bucketConfigs;

    /**
     * RateLimitFilter 建構子
     *
     * @param redisConnectionFactory     Redis 連線工廠（Lettuce 實作）
     * @param handlerExceptionResolver   Spring MVC 異常解析器，用於將 Filter 內的異常委派給 GlobalExceptionHandler
     */
    public RateLimitFilter(
            RedisConnectionFactory redisConnectionFactory,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    /**
     * 建立指定容量與時間視窗的 BucketConfiguration
     * Token Bucket 模型：在 duration 時間視窗內補充 capacity 個 token，上限亦為 capacity。
     *
     * @param capacity 在時間視窗內允許的最大請求數
     * @param duration 時間視窗長度
     * @return BucketConfiguration 實體
     */
    private static BucketConfiguration buildConfig(int capacity, Duration duration) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(capacity, duration))
                .build();
    }

    /**
     * 初始化 Bucket4j Lettuce ProxyManager
     *
     * 從 Spring Boot 的 LettuceConnectionFactory 取得底層 RedisClient，
     * 建立專用連線與 ProxyManager。
     * Bucket 的 TTL 由 basedOnTimeForRefillingBucketUpToMax 自動計算（補滿時間 + 30 秒緩衝）。
     */
    @PostConstruct
    public void init() {
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
        RedisClient nativeClient = (RedisClient) lettuceFactory.getRequiredNativeClient();

        // 建立 String Key / byte[] Value 的專用連線供 Bucket4j 使用
        lettuceConnection = nativeClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        // 使用 8.11+ 正式入口 Bucket4jLettuce.casBasedBuilder()
        proxyManager = Bucket4jLettuce.casBasedBuilder(lettuceConnection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(30)))
                .build();

        bucketConfigs = Map.of(
                "/api/v1/auth/login",                   buildConfig(loginCapacity, Duration.ofMinutes(1)),
                "/api/v1/auth/register",                buildConfig(5, Duration.ofMinutes(1)),
                "/api/v1/auth/refresh",                 buildConfig(10, Duration.ofMinutes(1)),
                "/api/v1/auth/oauth2/token",            buildConfig(5, Duration.ofMinutes(1)),
                "/api/v1/auth/email/send-verification", buildConfig(3, Duration.ofMinutes(5)),
                "/api/v1/auth/password/forgot",         buildConfig(3, Duration.ofMinutes(5)),
                "/api/v1/auth/password/verify-otp",     buildConfig(5, Duration.ofMinutes(1)),
                "/api/v1/auth/password/reset",          buildConfig(5, Duration.ofMinutes(1))
        );

        log.info("RateLimitFilter 初始化完成，登入限制: {}/min，監控路徑: {}", loginCapacity, RATE_LIMITED_PATHS);
    }

    /**
     * 釋放 Lettuce 連線資源
     */
    @PreDestroy
    public void destroy() {
        if (lettuceConnection != null && lettuceConnection.isOpen()) {
            lettuceConnection.close();
            log.info("RateLimitFilter Lettuce 連線已關閉");
        }
    }

    /**
     * 執行頻率限制檢查
     *
     * 依請求路徑與客戶端 IP 取得對應的 Bucket，嘗試消費 1 個 token。
     * 若 bucket 耗盡則設置 Retry-After 標頭並委派異常給 HandlerExceptionResolver 回傳 HTTP 429。
     *
     * @param request     HttpServletRequest 請求物件
     * @param response    HttpServletResponse 回應物件
     * @param filterChain 過濾器鏈
     * @throws ServletException Servlet 處理異常
     * @throws IOException      IO 操作異常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // 正規化路徑：移除尾隨斜線，確保與 BUCKET_CONFIGS key 一致
        String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String clientIp = getClientIp(request);

        // 依路徑最後一段命名 Redis key，例如：rate_limit:login:192.168.1.1
        String endpoint = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        String bucketKey = "rate_limit:" + endpoint + ":" + clientIp;

        BucketConfiguration config = bucketConfigs.get(normalizedPath);
        // 防禦性檢查：若路徑未在 BUCKET_CONFIGS 中，跳過限流避免 NullPointerException
        if (config == null) {
            log.warn("RateLimitFilter: 路徑 {} 無對應 BucketConfiguration，跳過限流", normalizedPath);
            filterChain.doFilter(request, response);
            return;
        }
        BucketProxy bucket = proxyManager.builder().build(bucketKey, config);

        if (!bucket.tryConsume(1)) {
            log.warn("請求頻率超限 - IP: {}, 路徑: {}", clientIp, path);
            response.setHeader("Retry-After", "60");
            handlerExceptionResolver.resolveException(
                    request, response, null, new RateLimitExceededException());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判斷是否跳過此 Filter
     * 只對 RATE_LIMITED_PATHS 內的路徑執行限流，其他路徑直接放行。
     *
     * @param request HttpServletRequest 請求物件
     * @return 若路徑不在限流清單中則回傳 true（跳過）
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 移除尾隨斜線，確保尾隨斜線的請求也能正確命中 RATE_LIMITED_PATHS
        String normalized = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        return !RATE_LIMITED_PATHS.contains(normalized);
    }

    /**
     * 提取客戶端真實 IP 地址
     *
     * 優先讀取反向代理設置的 X-Forwarded-For，其次 X-Real-IP，最後使用 TCP 連線的 remoteAddr。
     * 取得後交由 sanitizeIp() 處理，確保 IP 字串可安全用於 Redis key。
     *
     * @param request HttpServletRequest 請求物件
     * @return 已清理的客戶端 IP 字串
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For 格式可能為 "client, proxy1, proxy2"，取第一個
            return sanitizeIp(xff.split(",")[0].trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return sanitizeIp(realIp.trim());
        }
        return sanitizeIp(request.getRemoteAddr());
    }

    /**
     * 清理 IP 字串以安全用於 Redis key
     *
     * IPv6 loopback（::1 / 0:0:0:0:0:0:0:1）統一轉為 127.0.0.1；
     * 其餘 IPv6 地址將 : 替換為 -，避免破壞 Redis key 的 namespace 層級結構。
     *
     * @param ip 原始 IP 字串
     * @return 可安全用於 Redis key 的 IP 字串
     */
    private String sanitizeIp(String ip) {
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip.replace(":", "-");
    }
}
