package com.login.main.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新暱稱請求 DTO
 *
 * 用於 PATCH /api/v1/users/me，僅允許修改使用者的顯示暱稱。
 */
@Data
public class UpdateNicknameRequest {

    /**
     * 新暱稱
     * 不可為空白，長度限制 1 至 50 字元。
     */
    @NotBlank(message = "暱稱不可為空白")
    @Size(min = 1, max = 50, message = "暱稱長度須介於 1 至 50 字元")
    private String nickname;
}
