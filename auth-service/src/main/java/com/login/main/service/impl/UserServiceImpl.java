package com.login.main.service.impl;

import com.login.main.common.error.ErrorCode;
import com.login.main.common.result.Result;
import com.login.main.dto.response.UserEmailInfoDTO;
import com.login.main.dto.response.UserProfileResponse;
import com.login.main.entity.User;
import com.login.main.repository.UserRepository;
import com.login.main.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用者個人資料服務實作
 *
 * 實作個人資料查詢、暱稱更新與密碼更新的業務邏輯。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Result<UserProfileResponse> getProfile(String username) {
        return userRepository.findByUsernameWithSocialAccounts(username)
                .map(user -> Result.success(buildProfileResponse(user)))
                .orElse(Result.fail(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Result<Void> updateNickname(String username, String nickname) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setNickname(nickname);
                    userRepository.save(user);
                    log.info("暱稱更新成功 - 使用者: {}", username);
                    return Result.<Void>success();
                })
                .orElse(Result.fail(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Result<Void> updatePassword(String username, String currentPassword, String newPassword) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    // 已有密碼的使用者：驗證現有密碼
                    if (user.getPassword() != null) {
                        if (currentPassword == null || currentPassword.isBlank()) {
                            return Result.<Void>fail(ErrorCode.PASSWORD_REQUIRED);
                        }
                        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                            log.warn("密碼更新失敗，目前密碼錯誤 - 使用者: {}", username);
                            return Result.<Void>fail(ErrorCode.INCORRECT_PASSWORD);
                        }
                    }

                    user.setPassword(passwordEncoder.encode(newPassword));
                    userRepository.save(user);
                    log.info("密碼更新成功 - 使用者: {}", username);
                    return Result.<Void>success();
                })
                .orElse(Result.fail(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Result<UserEmailInfoDTO> searchByEmail(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.isVerified() && !user.isBlacklisted())
                .map(user -> Result.success(new UserEmailInfoDTO(user.getID(), user.getEmail(), user.getUsername())))
                .orElse(Result.fail(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 將 User 實體轉換為 UserProfileResponse
     *
     * @param user 使用者實體
     * @return UserProfileResponse DTO
     */
    private UserProfileResponse buildProfileResponse(User user) {
        List<String> providers = new ArrayList<>();

        // 有密碼代表支援本地登入
        if (user.getPassword() != null) {
            providers.add("LOCAL");
        }

        // 加入所有已綁定的 OAuth2 提供者
        user.getSocialAccounts().forEach(sa -> providers.add(sa.getProvider().name()));

        return UserProfileResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .isVerified(user.isVerified())
                .providers(providers)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
