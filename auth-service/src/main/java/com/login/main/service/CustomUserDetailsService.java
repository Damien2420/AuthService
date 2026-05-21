package com.login.main.service;

import lombok.RequiredArgsConstructor;

import com.login.main.entity.User;
import com.login.main.repository.UserRepository;
import com.login.main.security.CustomUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * 根據使用者名稱載入使用者資訊
     *
     * 回傳包含完整實體欄位（userId、email）的 CustomUserDetails，使 SecurityContext 中可直接取得這些資訊。
     *
     * @param username 使用者名稱
     * @return 包含完整使用者資訊的 CustomUserDetails
     * @throws UsernameNotFoundException 使用者不存在時拋出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return new CustomUserDetails(user);
    }
}
