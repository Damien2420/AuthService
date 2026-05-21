package com.login.main.security;

import com.login.main.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 自訂 UserDetails 實作
 *
 * 包裝完整的 User 實體，使 Spring Security 的 SecurityContext 中
 * 可直接存取 userId 與 email，避免登入流程中發生重複的資料庫查詢。
 *
 * @see com.login.main.service.CustomUserDetailsService
 */
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 從 User 實體建構 CustomUserDetails
     *
     * @param user 資料庫查詢出的完整 User 實體
     */
    public CustomUserDetails(User user) {
        this.userId = user.getID();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.enabled = user.isVerified();
        this.accountNonLocked = !user.isBlacklisted();
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 取得使用者 UUID
     *
     * @return 使用者的唯一識別碼
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * 取得使用者電子郵件
     *
     * @return 電子郵件地址
     */
    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
