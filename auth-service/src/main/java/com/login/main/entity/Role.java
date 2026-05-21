package com.login.main.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色實體類別
 * 
 * 映射至資料庫的 roles 資料表，用於定義系統權限層級。
 * 遵循 Spring Security 慣例，名稱通常以 "ROLE_" 為前綴 (如 ROLE_USER)。
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "ROLE_USER", "ROLE_ADMIN"
    // 以 ROLE_ 開頭是 Spring Security 的慣例

    public Role(String name) {
        this.name = name;
    }
}
