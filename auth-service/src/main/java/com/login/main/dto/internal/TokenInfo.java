package com.login.main.dto.internal;

// record -> 由 Java 自行產生的DTO類別 (不可變)
// 會自動產生建構子、getters、setters、equals、hashCode、toString 等方法
public record TokenInfo(
    String accessToken,
    String refreshToken,
    String username,
    long refreshTokenMaxAge
) {}
