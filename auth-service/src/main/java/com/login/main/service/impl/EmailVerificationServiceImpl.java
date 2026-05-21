package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.config.AppProperties;
import com.login.main.enums.RedisKeyPrefix;
import com.login.main.repository.UserRepository;
import com.login.main.service.EmailService;
import com.login.main.service.EmailVerificationService;
import com.login.main.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Email 驗證服務實作
 *
 * 負責「先驗證再註冊」流程中的 OTP 發送與驗證。
 * OTP 的 Redis 操作委派給 {@link OtpService}，郵件發送委派給 {@link EmailService}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties appProperties;

    /**
     * 發送 Email 驗證 OTP 至指定信箱
     *
     * @param email 使用者填入的電子郵件地址
     * @return 成功時回傳 {@code Result.success()}，Email 已被使用時回傳 {@code EMAIL_ALREADY_EXISTS}
     */
    @Override
    public Result<Void> sendVerificationOtpEmail(String email) {
        log.info("Email 驗證 OTP 發送請求 - Email: {}", email);

        // 若 Email 已被使用，提前回傳錯誤
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("Email 驗證 OTP 發送失敗 - Email 已存在: {}", email);
            return Result.fail(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String otp = otpService.issueOtp(email, RedisKeyPrefix.OTP_EMAIL_VERIFICATION);

        emailService.sendOtpEmail(email, otp, RedisKeyPrefix.OTP_EMAIL_VERIFICATION);
        log.info("Email 驗證 OTP 發送完成 - Email: {}", email);

        return Result.success();
    }

    /**
     * 驗證 Email OTP 並一次性消費
     *
     * @param email 使用者電子郵件地址
     * @param otp   使用者輸入的 6 位數驗證碼
     * @return 驗證成功回傳 {@code Result.success()}，OTP 無效或過期回傳 {@code EMAIL_VERIFICATION_OTP_INVALID}
     */
    @Override
    public Result<Void> verifyAndConsume(String email, String otp) {
        log.info("Email 驗證 OTP 驗證請求 - Email: {}", email);

        Result<Void> verifyResult = otpService.verifyAndConsume(email, otp, RedisKeyPrefix.OTP_EMAIL_VERIFICATION);
        if (verifyResult.isFailed()) {
            log.warn("Email 驗證 OTP 驗證失敗 - Email: {}", email);
            return Result.fail(ErrorCode.EMAIL_VERIFICATION_OTP_INVALID);
        }

        log.info("Email 驗證 OTP 驗證成功 - Email: {}", email);
        return Result.success();
    }

    /**
     * 重新寄出含驗證連結的 Email 驗證信
     *
     * 同一 username 5 分鐘內最多允許 3 次請求，超過限制回傳 {@code RATE_LIMIT_EXCEEDED}。
     *
     * @param username 使用者帳號名稱
     * @return 成功時回傳 {@code Result.success()}，失敗時回傳對應錯誤
     */
    @Override
    public Result<Void> resendVerificationLink(String username) {
        log.info("重新寄出 Email 驗證連結請求 - 使用者: {}", username);

        // 頻率限制：同一 username 5 分鐘內最多 3 次
        String rateLimitKey = RedisKeyPrefix.EMAIL_RESEND_RATE_LIMIT.buildKey(username);
        Long count = stringRedisTemplate.opsForValue().increment(rateLimitKey);
        if (count == 1) {
            // 第一次計數時設定 TTL
            stringRedisTemplate.expire(rateLimitKey,
                    RedisKeyPrefix.EMAIL_RESEND_RATE_LIMIT.getTtlMinutes(),
                    TimeUnit.MINUTES);
        }
        if (count > 3) {
            log.warn("Email 驗證連結寄送超過頻率限制 - 使用者: {}", username);
            return Result.fail(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.isVerified()) {
                        log.warn("Email 驗證連結寄送失敗 - 帳號已驗證: {}", username);
                        return Result.<Void>fail(ErrorCode.EMAIL_ALREADY_VERIFIED);
                    }

                    String token = UUID.randomUUID().toString();
                    String redisKey = RedisKeyPrefix.EMAIL_VERIFICATION_LINK.buildKey(token);
                    stringRedisTemplate.opsForValue().set(
                            redisKey,
                            user.getEmail(),
                            RedisKeyPrefix.EMAIL_VERIFICATION_LINK.getTtlMinutes(),
                            TimeUnit.MINUTES
                    );

                    String verificationUrl = appProperties.getFrontendUrl() + "/verify-email?token=" + token;
                    emailService.sendVerificationLinkEmail(user.getEmail(), verificationUrl);

                    log.info("Email 驗證連結寄送完成 - 使用者: {}", username);
                    return Result.<Void>success();
                })
                .orElseGet(() -> {
                    log.warn("Email 驗證連結寄送失敗 - 使用者不存在: {}", username);
                    return Result.fail(ErrorCode.USER_NOT_FOUND);
                });
    }

    /**
     * 驗證 Email 驗證連結中的 token 並完成帳號驗證
     *
     * @param token 驗證連結中的 UUID token
     * @return 成功時回傳 {@code Result.success()}，失敗時回傳對應錯誤
     */
    @Override
    @Transactional
    public Result<Void> verifyByToken(String token) {
        log.info("Email 驗證連結 token 驗證請求");

        String redisKey = RedisKeyPrefix.EMAIL_VERIFICATION_LINK.buildKey(token);
        String email = stringRedisTemplate.opsForValue().get(redisKey);

        if (email == null) {
            log.warn("Email 驗證連結 token 無效或已過期");
            return Result.fail(ErrorCode.VERIFICATION_LINK_INVALID);
        }

        return userRepository.findByEmail(email)
                .map(user -> {
                    user.setVerified(true);
                    userRepository.save(user);
                    stringRedisTemplate.delete(redisKey);
                    log.info("Email 驗證成功 - Email: {}", email);
                    return Result.<Void>success();
                })
                .orElseGet(() -> {
                    // 不回傳 USER_NOT_FOUND，避免洩露帳號是否存在（token 枚舉防護）
                    log.warn("Email 驗證失敗 - token 對應使用者不存在（資料異常）");
                    return Result.fail(ErrorCode.VERIFICATION_LINK_INVALID);
                });
    }
}
