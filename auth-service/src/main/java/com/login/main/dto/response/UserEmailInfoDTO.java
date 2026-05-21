package com.login.main.dto.response;

import java.util.UUID;

/**
 * 以 Email 查詢使用者的回應 DTO
 *
 * 僅回傳識別用資訊，供其他服務（例如 kanban-service 邀請成員流程）使用。
 *
 * @param userId   使用者 UUID
 * @param email    使用者電子郵件
 * @param username 使用者名稱
 */
public record UserEmailInfoDTO(UUID userId, String email, String username) {
}
