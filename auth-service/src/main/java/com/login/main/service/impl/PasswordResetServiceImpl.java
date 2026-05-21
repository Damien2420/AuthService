package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.entity.User;
import com.login.main.enums.RedisKeyPrefix;
import com.login.main.repository.UserRepository;
import com.login.main.service.EmailService;
import com.login.main.service.OtpService;
import com.login.main.service.PasswordResetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 密碼重設服務實作
 *
 * 實作忘記密碼三階段流程，並透過 Redis 管理以下狀態：
 * - {@link RedisKeyPrefix#OTP_PASSWORD_RESET}{email}  → 6 位 OTP，TTL 5 分鐘（由 OtpService 管理）
 * - pwd_reset:token:{uuid}                            → email，TTL 10 分鐘
 * - pwd_reset:invalidation:{user}                     → 重設時間戳（epochMillis），TTL 7 天
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String TOKEN_KEY_PREFIX = "pwd_reset:token:";
    private static final String INVALIDATION_KEY_PREFIX = "pwd_reset:invalidation:";

    private static final long TOKEN_TTL_MINUTES = 10;
    private static final long INVALIDATION_TTL_DAYS = 7;

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 發送 OTP 驗證碼至使用者信箱
     *
     * @param email 使用者電子郵件
     * @return 成功時回傳 Result.success()，帳號不存在時一樣回傳 Result.success()，但不寄送信件
     */
    @Override
    public Result<Void> sendOtp(String email) {
        log.info("忘記密碼請求 - Email: {}", email);

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("忘記密碼請求 - Email 不存在，不寄送信件: {}", email);
            return Result.success();
        }

        String otp = otpService.issueOtp(email, RedisKeyPrefix.OTP_PASSWORD_RESET);

        emailService.sendOtpEmail(email, otp, RedisKeyPrefix.OTP_PASSWORD_RESET);
        log.info("OTP 發送完成 - Email: {}", email);

        return Result.success();
    }

    /**
     * 驗證 OTP 並核發 Reset Token
     *
     * @param email 使用者電子郵件
     * @param otp   使用者輸入的 6 位數驗證碼
     * @return 成功時回傳含 Reset Token 的 Result，失敗時回傳 OTP_INVALID
     */
    @Override
    public Result<String> verifyOtp(String email, String otp) {
        log.info("OTP 驗證請求 - Email: {}", email);

        // 委派驗證與消費至 OtpService
        Result<Void> verifyResult = otpService.verifyAndConsume(email, otp, RedisKeyPrefix.OTP_PASSWORD_RESET);
        if (verifyResult.isFailed()) {
            log.warn("OTP 驗證失敗 - Email: {}", email);
            return Result.fail(ErrorCode.OTP_INVALID);
        }

        log.debug("OTP 驗證成功 - Email: {}", email);

        // 核發 Reset Token（UUID），存入 Redis，TTL 10 分鐘
        String resetToken = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + resetToken;
        try {
            redisTemplate.opsForValue().set(tokenKey, email, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Reset Token 寫入 Redis 失敗 - Email: {}, 錯誤: {}", email, e.getMessage());
            return Result.fail(ErrorCode.INTERNAL_ERROR);
        }
        log.info("Reset Token 核發完成 - Email: {}, TTL: {} 分鐘", email, TOKEN_TTL_MINUTES);

        return Result.success(resetToken);
    }

    /**
     * 重設密碼並使所有現有 Session 失效
     *
     * @param resetToken  由 verifyOtp 核發的一次性重設權杖
     * @param newPassword 新密碼（明文，方法內部將進行 BCrypt 加密）
     * @return 成功時回傳 Result.success()，Token 無效時回傳 RESET_TOKEN_INVALID
     */
    @Override
    @Transactional
    public Result<Void> resetPassword(String resetToken, String newPassword) {
        log.info("密碼重設請求");

        // 從 Redis 取得對應的 email
        String tokenKey = TOKEN_KEY_PREFIX + resetToken;
        Object emailObj = redisTemplate.opsForValue().get(tokenKey);

        if (emailObj == null) {
            log.warn("密碼重設失敗 - Reset Token 不存在或已過期");
            return Result.fail(ErrorCode.RESET_TOKEN_INVALID);
        }

        String email = emailObj.toString();

        // 查找使用者
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.error("密碼重設失敗 - Reset Token 對應的 Email 找不到使用者: {}", email);
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        User user = userOpt.get();
        String username = user.getUsername();

        // 更新密碼
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("密碼更新完成 - 使用者: {}", username);

        // 消費 Reset Token，立即刪除
        redisTemplate.delete(tokenKey);
        log.debug("Reset Token 已消費並從 Redis 刪除");

        // 記錄密碼重設時間戳，使所有現有 JWT Token 失效（TTL 7 天）
        String invalidationKey = INVALIDATION_KEY_PREFIX + username;
        redisTemplate.opsForValue().set(invalidationKey, String.valueOf(System.currentTimeMillis()), INVALIDATION_TTL_DAYS, TimeUnit.DAYS);
        log.info("密碼重設時間戳已記錄，所有現有 Session 將在下次請求時失效 - 使用者: {}", username);

        return Result.success();
    }

    /**
     * 檢查指定使用者的 JWT Token 是否在密碼重設後已失效
     *
     * @param username 使用者名稱（JWT subject）
     * @param issuedAt Token 的發行時間
     * @return 若 Token 在重設前發出則回傳 true，否則回傳 false
     * @throws RuntimeException 當 Redis 查詢異常時拋出
     */
    @Override
    public boolean isTokenInvalidatedByPasswordReset(String username, Date issuedAt) {
        try {
            String invalidationKey = INVALIDATION_KEY_PREFIX + username;
            Object storedTimestamp = redisTemplate.opsForValue().get(invalidationKey);

            // key 不存在：此使用者從未重設過密碼，直接放行
            if (storedTimestamp == null) {
                return false;
            }

            long resetTime = Long.parseLong(storedTimestamp.toString());
            boolean isInvalidated = issuedAt.getTime() < resetTime;

            if (isInvalidated) {
                log.debug("Token 在密碼重設前發出，判定為已失效 - 使用者: {}, Token issuedAt: {}, 重設時間: {}", username, issuedAt.getTime(), resetTime);
            }

            return isInvalidated;
        } catch (Exception e) {
            log.error("密碼重設失效查詢異常 - 使用者: {}, 錯誤: {}", username, e.getMessage());
            throw new RuntimeException("密碼重設失效檢查服務異常", e);
        }
    }
}