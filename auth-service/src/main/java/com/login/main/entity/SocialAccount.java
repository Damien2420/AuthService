package com.login.main.entity;

import com.login.main.enums.Providers;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 社交帳號關聯實體
 * 映射至資料庫的 social_accounts 資料表，儲存第三方 OAuth2 供應商提供的唯一身分標識。
 * 每個實體皆多對一關聯至特定的 User 主帳號。
 */
@Entity
@Table(name = "social_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Providers provider;

    @Column(name = "provider_id", nullable = false)
    private String providerID;

    @Column(nullable = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
