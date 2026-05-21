package com.login.main.service.impl;

import com.login.main.config.AppProperties;
import com.login.main.enums.RedisKeyPrefix;
import com.login.main.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 郵件發送服務實作
 *
 * 使用 Spring 的 JavaMailSender 透過 SMTP 發送郵件。
 * 寄件人地址由 AppProperties 注入，SMTP 伺服器設定由 application.properties 管理。
 * 透過 {@link RedisKeyPrefix} 區分不同業務場景的郵件主旨與內文。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    /**
     * 發送 OTP 驗證碼郵件（依業務場景切換郵件主旨與內文）
     *
     * @param to        收件人電子郵件地址
     * @param otp       6 位數一次性驗證碼
     * @param keyPrefix 決定郵件內容的業務場景前綴
     */
    @Override
    public void sendOtpEmail(String to, String otp, RedisKeyPrefix keyPrefix) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.getMail().getFrom());
        message.setTo(to);

        switch (keyPrefix) {
            case OTP_EMAIL_VERIFICATION -> {
                message.setSubject("【Email 驗證】您的驗證碼");
                message.setText(
                        "您好，\n\n" +
                        "感謝您註冊！請使用以下驗證碼完成 Email 驗證：\n\n" +
                        "  " + otp + "\n\n" +
                        "此驗證碼將於 5 分鐘後失效，請勿將此碼透露給他人。\n\n" +
                        "若您並未申請此驗證，請忽略此封郵件。\n\n\n\n" +
                        "此郵件為自動發送郵件，請勿回復"
                );
            }
            case OTP_PASSWORD_RESET -> {
                message.setSubject("【密碼重設】您的驗證碼");
                message.setText(
                        "您好，\n\n" +
                        "您的密碼重設驗證碼為：\n\n" +
                        "  " + otp + "\n\n" +
                        "此驗證碼將於 5 分鐘後失效，請勿將此碼透露給他人。\n\n" +
                        "若您並未申請重設密碼，請忽略此封郵件。\n\n\n\n" +
                        "此郵件為自動發送郵件，請勿回復"
                );
            }
            default -> {
                log.warn("未知的 OTP 場景: {}", keyPrefix.name());
            }
        }

        log.info("發送 OTP 郵件 - 收件人: {}, 場景: {}", to, keyPrefix.name());
        mailSender.send(message);
        log.debug("OTP 郵件寄送完成 - 收件人: {}", to);
    }

    /**
     * 發送含驗證連結的 Email 驗證信
     *
     * @param to              收件人電子郵件地址
     * @param verificationUrl 使用者點擊後可完成驗證的完整 URL
     */
    @Override
    public void sendVerificationLinkEmail(String to, String verificationUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.getMail().getFrom());
        message.setTo(to);
        message.setSubject("【Email 驗證】請點擊連結完成驗證");
        message.setText(
                "您好，\n\n" +
                "請點擊以下連結完成 Email 驗證：\n\n" +
                "  " + verificationUrl + "\n\n" +
                "此連結將於 24 小時後失效，請盡快完成驗證。\n\n" +
                "若您並未申請此驗證，請忽略此封郵件。\n\n\n\n" +
                "此郵件為自動發送郵件，請勿回復"
        );

        log.info("發送 Email 驗證連結郵件 - 收件人: {}", to);
        mailSender.send(message);
        log.debug("Email 驗證連結郵件寄送完成 - 收件人: {}", to);
    }
}