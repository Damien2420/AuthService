# Auth Service

全端登入系統，以 Spring Boot 構建後端認證服務，以 React 構建登入前端。涵蓋帳號密碼、Email 驗證、OAuth2 社群登入（Google / Discord / LINE）、忘記密碼等完整認證流程。

---

## 技術棧

### 後端

| 分類 | 技術 |
|---|---|
| 核心框架 | Java 17 / Spring Boot 4.0.1 |
| 安全驗證 | Spring Security 6 / JWT / OAuth2 Client |
| 資料持久化 | Spring Data JPA / PostgreSQL / Flyway |
| 快取與限流 | Spring Data Redis / Bucket4j |
| Email | Spring Mail (SMTP) |
| 人機驗證 | Cloudflare Turnstile |
| Google 登入 | Google API Client (GIS One Tap ID Token 驗證) |
| API 文件 | SpringDoc OpenAPI (Swagger UI) |
| 測試 | JUnit 5 / Mockito / Testcontainers |
| 其他 | Lombok / spring-dotenv / Bean Validation |

### 前端

| 分類 | 技術 |
|---|---|
| 核心框架 | React 18 / TypeScript / Vite 7 |
| 路由 | React Router DOM v7 |
| 表單驗證 | React Hook Form / Zod |
| HTTP 客戶端 | Axios（含自動 Token Refresh 攔截器） |
| UI 元件 | Radix UI / shadcn/ui |
| 動畫 | Motion / Animate UI |
| 樣式 | Tailwind CSS v4 |
| 通知 | Sonner |
| 主題 | next-themes（深色 / 淺色模式） |
| 人機驗證 | Cloudflare Turnstile |
| Google 登入 | Google Identity Services (GIS One Tap) |
| 錯誤監控 | Sentry React |

---

## 後端架構設計

### Result Pattern

Service 層採用 Result Monad 方式設計流程，使用 `Result<T>` 取代 try-catch 例外拋出，以鏈式呼叫傳遞成功值或錯誤碼：

```java
emailVerificationService.verifyAndConsume(email, verificationCode)
    .bind(unused -> validateIfUserExists(email, username))
    .bind(unused -> createUser(email, username, password))
    .bind(this::save)
    .map(user -> jwtUtil.generateTokenResponse(...))
    .orThrow(AppException::new);
```

### Fail-open / Fail-closed 策略

Redis 發生異常時，不同場景採用不同的容錯策略：

| 場景 | 策略 | 理由 |
|---|---|---|
| 登出時黑名單寫入失敗 | Fail-open（登出仍成功，Cookie 仍清除） | 強制使用者無法登出的體驗比偶發的安全風險更差 |
| Token Refresh 時黑名單查詢失敗 | Fail-closed（拒絕刷新） | 無法確認 Token 是否已被吊銷，寧可拒絕 |
| 密碼重設時間戳查詢失敗 | Fail-closed（拒絕刷新） | 無法確認 Token 是否在重設前簽發 |

---

## 前端架構設計

### AuthContext — Token 記憶體儲存

AccessToken 存於 React state（記憶體），不寫入 localStorage，避免 XSS 攻擊竊取 Token。`AuthContext` 負責：

- 管理 `accessToken` / `username` 狀態
- 初始化時自動呼叫 `/api/v1/auth/refresh`，透過 HttpOnly Cookie 中的 RefreshToken 恢復登入狀態
- 將 token getter / updater 注入 Axios 客戶端，讓攔截器能自動附加與更新 Token

### Axios 攔截器 — 自動 Token Refresh

Request 攔截器自動在 `Authorization` 標頭附上 AccessToken。Response 攔截器在收到 401 時，自動呼叫 `/refresh` 取得新 Token 後重試原請求，整個流程對業務邏輯透明。

### 錯誤邊界

`GlobalErrorFallback` 包覆整個應用，`SectionErrorFallback` 包覆各區塊，確保局部錯誤不會崩潰整個頁面。

---

## 後端專案結構

```
src/main/java/com/login/main/
├── LoginApplication.java
├── common/
│   ├── error/
│   │   ├── AppException.java           # 業務邏輯統一例外
│   │   ├── ErrorCode.java              # 錯誤碼枚舉（含 HTTP 狀態碼與訊息）
│   │   └── RateLimitExceededException.java
│   └── result/
│       └── Result.java                 # 封裝成功/失敗的 Monad 回傳型別
├── config/
│   ├── AppConfig.java                  # Bean 通用設定（PasswordEncoder、AuthenticationManager 等）
│   ├── AppProperties.java              # 自定義設定屬性（cookie、turnstile、mail、sentry）
│   ├── OpenApiConfig.java              # Swagger / OpenAPI 設定
│   ├── RedisConfig.java                # Redis 連線與序列化設定
│   └── SecurityConfig.java            # Security 過濾鏈、CORS、OAuth2 設定
├── controller/
│   ├── AuthController.java             # 認證相關 API（/api/v1/auth）
│   ├── UserController.java             # 使用者資料 API（/api/v1/users）
│   └── SentryTunnelController.java     # Sentry Tunnel 代理端點
├── dto/
│   ├── internal/
│   │   └── TokenInfo.java              # 服務層內部 Token 傳遞 Record
│   ├── request/                        # 請求 DTO（含 Bean Validation 標註）
│   └── response/                       # 回應 DTO
├── entity/
│   ├── User.java                       # 使用者實體
│   ├── Role.java                       # 角色實體
│   └── SocialAccount.java             # 社群帳號連結實體
├── enums/
│   ├── Providers.java                  # OAuth2 供應商（GOOGLE、DISCORD、LINE）
│   ├── RedisKeyPrefix.java             # Redis Key 前綴枚舉
│   └── RoleType.java                   # 角色類型（USER、ADMIN）
├── filter/
│   ├── JwtAuthenticationFilter.java    # JWT 解析與驗證過濾器
│   ├── IdempotencyFilter.java          # 請求冪等性過濾器（X-Idempotency-Key）
│   ├── RateLimitFilter.java            # IP 級別頻率限制（Bucket4j + Redis）
│   └── TraceIdFilter.java              # 請求追蹤 ID 注入（MDC）
├── handler/
│   ├── GlobalExceptionHandler.java     # 全域例外處理器（@RestControllerAdvice）
│   ├── JwtErrorHandlerEntry.java       # JWT 驗證失敗入口點（401）
│   └── OAuth2AuthenticationHandler.java # OAuth2 登入成功後的回調處理
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   └── SocialAccountRepository.java
├── security/
│   └── CustomUserDetails.java          # Spring Security UserDetails 實作
├── service/
│   ├── AuthService.java                # 認證核心（登入、註冊、Token 刷新、登出）
│   ├── EmailService.java               # Email 寄送
│   ├── EmailVerificationService.java   # Email 驗證連結流程
│   ├── GoogleIdTokenService.java       # GIS ID Token 驗證
│   ├── LoginAttemptService.java        # 登入失敗次數追蹤與帳號鎖定
│   ├── OAuth2CodeService.java          # OAuth2 一次性授權碼管理（Redis）
│   ├── OtpService.java                 # OTP 生成與驗證（Redis）
│   ├── PasswordResetService.java       # 忘記密碼流程
│   ├── SocialAccountService.java       # 社群帳號連結管理
│   ├── TokenBlacklistService.java      # Token 黑名單（Redis）
│   ├── TurnstileService.java           # Cloudflare Turnstile 人機驗證
│   ├── UserService.java                # 使用者資料查詢與更新
│   └── CustomUserDetailsService.java   # Spring Security UserDetailsService 實作
└── util/
    ├── JwtUtil.java                    # JWT 生成、解析、驗證
    ├── HttpCookieUtil.java             # HttpOnly Cookie 操作工具
    └── DateTimeUtil.java               # 時間轉換工具
```

## 前端專案結構

```
src/
├── contexts/
│   └── AuthContext.tsx          # 認證狀態管理（Token 記憶體儲存、自動 refresh）
├── pages/
│   ├── Login.tsx                # 登入頁（帳密、OAuth2、GIS One Tap）
│   ├── Register.tsx             # 註冊頁（含 Email OTP 驗證）
│   ├── Welcome.tsx              # 登入後歡迎頁 (暫定)
│   ├── MainPage.tsx             # 根路由入口（未登入導向 /login）
│   ├── OAuthCallback.tsx        # OAuth2 授權碼換取 JWT
│   ├── ResetPassword.tsx        # 忘記密碼流程
│   └── VerifyEmail.tsx          # Email 驗證
├── components/
│   ├── ui/                      # shadcn/ui 基礎元件
│   ├── animate-ui/              # 動畫強化元件（Animate UI）
│   └── user-defined/            # 自訂業務元件（GlobalErrorFallback、SectionErrorFallback、forgot-credentials-dialog 等）
├── hooks/
│   ├── use-turnstile.tsx        # Cloudflare Turnstile 整合 Hook
│   ├── use-auto-height.tsx      # 動態高度計算 Hook
│   ├── use-is-in-view.tsx       # Intersection Observer Hook
│   └── use-controlled-state.tsx # 受控/非受控狀態整合 Hook
├── utils/
│   ├── api-util.ts              # Axios 客戶端（含自動 Token Refresh 攔截器）
│   ├── gis-util.ts              # Google GIS One Tap 工具
│   ├── api-error-handler.ts     # API 錯誤統一處理
│   ├── logger.ts                # 日誌工具（dev console / production Sentry）
│   └── validation-util.ts       # 表單驗證工具
├── types/
│   ├── auth.ts                  # 認證相關 TypeScript 型別定義
│   └── api-error.ts             # API 錯誤回應型別定義
├── lib/
│   ├── utils.ts                 # shadcn/ui 工具函式（cn）
│   └── get-strict-context.tsx   # 嚴格模式 Context 工廠（避免 undefined 存取）
├── constants/
│   ├── api-paths.ts             # API 端點常數
│   └── error-mapping.ts         # 後端錯誤碼對應前端訊息
├── config/
│   └── env.ts                   # 環境變數集中讀取與驗證
└── instruments.ts               # Sentry 初始化
```

---

## API 端點

可至啟動後端後的 `後端URL/swagger` 查看 Swagger UI 完整文件。

### 認證模組 `/api/v1/auth`

| Method | 路徑 | 說明 | 需認證 |
|---|---|---|---|
| POST | `/register` | 使用者註冊（含 Turnstile 人機驗證） | 否 |
| POST | `/email/send-verification` | 發送 Email 驗證碼（OTP）至信箱 | 否 |
| POST | `/email/resend-verification` | 重新寄出 Email 驗證連結 | 否 |
| POST | `/email/verify` | 驗證 Email 連結 Token | 否 |
| POST | `/login` | 帳號密碼登入（含 Turnstile、帳號鎖定檢查） | 否 |
| POST | `/refresh` | 使用 HttpOnly Cookie 刷新 AccessToken | 否 |
| POST | `/logout` | 登出，將 Token 加入黑名單並清除 Cookie | Bearer Token |
| POST | `/oauth2/token` | OAuth2 一次性授權碼換取 JWT | 否 |
| POST | `/google/login` | GIS One Tap 登入（驗證 Google ID Token） | 否 |
| POST | `/password/forgot` | 忘記密碼，發送 OTP 至信箱 | 否 |
| POST | `/password/verify-otp` | 驗證 OTP，取得一次性 Reset Token | 否 |
| POST | `/password/reset` | 使用 Reset Token 重設密碼 | 否 |

### 使用者模組 `/api/v1/users`

所有端點需要 `Authorization: Bearer {token}`。

| Method | 路徑 | 說明 |
|---|---|---|
| GET | `/search?email=` | 以 Email 查詢使用者基本資訊 |
| GET | `/me` | 取得目前登入使用者的個人資料 |
| PATCH | `/me` | 更新暱稱 |
| PATCH | `/me/password` | 更新密碼（OAuth2 使用者首次設定可省略 currentPassword） |

---

## 安全機制

### Token 設計

- **AccessToken**：短效期 JWT，由 Response Body 回傳，前端存於 React 記憶體 state，非 localStorage。
- **RefreshToken**：長效期，由 `Set-Cookie` 以 HttpOnly + SameSite=Strict 設定，JS 無法存取。
- **Token 黑名單**：登出或密碼重設後，舊 Token 加入 Redis 黑名單立即失效。
- **Token Rotation**：每次 `/refresh` 後舊 RefreshToken 立即吊銷並換發全新一組 Token，防止 RefreshToken 被竊後長期有效。
- **密碼重設時間戳**：Redis 紀錄重設時間，使重設前簽發的所有 Token 失效。

### 頻率限制（Rate Limiting）

使用 Bucket4j Token Bucket 演算法搭配 Redis 分散式儲存，依來源 IP 限制各端點請求頻率：

| 端點 | 限制 |
|---|---|
| `/login` | 20 次 / 分鐘 |
| `/register` | 5 次 / 分鐘 |
| `/refresh` | 10 次 / 分鐘 |
| `/oauth2/token` | 5 次 / 分鐘 |
| `/email/send-verification` | 3 次 / 5 分鐘 |
| `/password/forgot` | 3 次 / 5 分鐘 |
| `/password/verify-otp` | 5 次 / 分鐘 |
| `/password/reset` | 5 次 / 分鐘 |

超過限制回傳 HTTP 429，附帶 `Retry-After: 60` 標頭。

### 帳號鎖定

登入連續失敗達閾值後，帳號暫時鎖定，防止暴力破解。失敗記錄存於 Redis。

### 其他

- **Cloudflare Turnstile**：登入與註冊端點加入人機驗證（前後端皆整合），防止自動化攻擊。
- **Idempotency Filter**：客戶端在請求標頭帶入 `X-Idempotency-Key`，後端將執行結果快取於 Redis。相同 Key 的重複請求（如網路重試、重複點擊）直接回傳快取結果而不重新執行業務邏輯，確保操作冪等性。
- **Sentry Tunnel**：前端 Sentry SDK 若直接打 Sentry CDN，容易被用戶端的 Ad-blocker 封鎖，導致錯誤事件遺失。`SentryTunnelController` 作為代理端點，將前端監控請求轉送至後端再轉發至 Sentry，繞過 Ad-blocker 限制，確保 Production 錯誤可觀測性。
- **Trace ID**：每個請求自動注入 `traceId` 至 MDC，方便 Log 追蹤。

---

## 資料庫 Schema

由 Flyway 管理，Migration 檔案位於 `src/main/resources/db/migration/`。

```sql
users          -- 使用者主表（id, email, username, password, nickname, is_verified, is_blacklisted）
roles          -- 角色表（USER、ADMIN）
user_roles     -- 使用者與角色的多對多關聯
social_accounts -- OAuth2 社群帳號連結（provider, provider_id, email, user_id）
```

---

## OAuth2 社群登入

### 標準 OAuth2 重定向流程

支援 Google、Discord、LINE 三個供應商，走 Spring Security OAuth2 Client 標準授權碼流程：

```
前端發起登入
  → Spring Security 重定向至第三方授權頁
  → 第三方回調 /login/oauth2/code/{provider}
  → OAuth2AuthenticationHandler 處理
      ├── 查詢 SocialAccount 是否已存在
      │     ├── 存在 → 取得對應 User
      │     └── 不存在 → 建立 User + 綁定 SocialAccount
      └── 發放一次性授權碼（Redis TTL 60 秒）
  → 重導向至前端 /auth/callback?code=xxx
  → 前端呼叫 POST /oauth2/token 換取 JWT
```

JWT 不直接附在重定向 URL 上，避免 Token 暴露於瀏覽器歷史記錄與伺服器存取日誌。

### GIS One Tap

Google 同時支援兩種登入方式：標準 OAuth2 重定向流程與 GIS One Tap，兩者共用相同的 Client ID。GIS One Tap 不走標準 OAuth2 重定向流程。前端直接從 Google 取得 ID Token 後，呼叫 `POST /google/login`，由後端使用 Google API Client 2.7.2 驗證 Token 並簽發 JWT。

### 供應商設定

| 供應商 | Client ID / Secret 環境變數 |
|---|---|
| Google | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` |
| Discord | `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` |
| LINE | `LINE_CLIENT_ID` / `LINE_CLIENT_SECRET` |

### 供應商特殊處理

| 供應商 | 問題 | 處理方式 |
|---|---|---|
| LINE | ID Token 使用 HS256 + Channel Secret 簽名，非標準 RS256 | `SecurityConfig` 註冊自訂 `JwtDecoderFactory`，使用 `NimbusJwtDecoder.withSecretKey()` 搭配 `MacAlgorithm.HS256` |
| LINE | 未綁定信箱的帳號 email 為 null | 產生合成信箱 `line_{sub}@noemail.invalid` |
| Discord | 部分帳號無 email | 產生合成信箱 `discord_{id}@noemail.invalid` |
| Discord | `name` 屬性已棄用 | 優先取 `global_name`，不存在才 fallback 至 `username` |

---

## 環境變數

### 後端（`auth-service/.env`）

複製 `.env.example` 並填入實際值：

```env
# JWT
JWT_SECRET=                          # Base64 編碼的 HMAC 密鑰（至少 256 bit）
JWT_EXPIRATION_MS=300000             # AccessToken 有效期（毫秒），預設 5 分鐘
JWT_REFRESH_EXPIRATION_MS=604800000  # RefreshToken 有效期（毫秒），預設 7 天

# 資料庫
DB_USERNAME=
DB_PASSWORD=

# OAuth2
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
DISCORD_CLIENT_ID=
DISCORD_CLIENT_SECRET=
LINE_CLIENT_ID=
LINE_CLIENT_SECRET=

# Email（SMTP）
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=

# Cloudflare Turnstile
TURNSTILE_SECRET_KEY=

# Sentry Tunnel（逗號分隔多個 Project ID，可留空）
SENTRY_ALLOWED_PROJECT_IDS=
```

### 前端（`frontend/.env`）

```env
VITE_API_BASE_URL=          # 後端 API 位址，例如 http://localhost:9000
VITE_GOOGLE_CLIENT_ID=      # 與後端共用同一組 Google Client ID
VITE_TURNSTILE_SITE_KEY=    # Cloudflare Turnstile Site Key（非 Secret Key）
VITE_SENTRY_DSN=            # Sentry DSN（可留空，留空則停用）
```

---

## 快速啟動

### 環境需求

- JDK 17+
- Node.js 20+ / pnpm
- Docker（啟動 PostgreSQL / Redis）

### 啟動步驟

```bash
# 1. 啟動依賴服務（PostgreSQL、Redis、RedisInsight）
docker-compose up -d

# 2. 啟動後端（port 9000）
cd auth-service
./mvnw spring-boot:run

# 3. 啟動前端（port 5173）
cd frontend
pnpm install
pnpm dev
```

Docker Compose 包含：
- PostgreSQL 15（port 5432）
- Redis 7（port 6379）
- RedisInsight（port 5540，瀏覽器管理介面）

Flyway 在後端啟動時自動執行 Migration，無需手動建表。

---

## 執行測試

```bash
cd auth-service
./mvnw test
```

整合測試使用 Testcontainers 自動啟動 PostgreSQL 容器，無需額外設定。

測試涵蓋範圍：
- `AuthControllerTest` — Controller 層 API 行為
- `AuthIntegrationTest` — 完整認證流程整合測試
- `AuthServiceTest` / 各 Service 單元測試
- `JwtAuthenticationFilterTest` / `RateLimitFilterTest` / `IdempotencyFilterTest` — Filter 行為
- `JwtUtilTest` — Token 生成與解析
