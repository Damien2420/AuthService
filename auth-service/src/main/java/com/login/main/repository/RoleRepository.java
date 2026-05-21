package com.login.main.repository;

import com.login.main.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 角色權限資料儲存庫
 * 負責管理系統中的角色實體 (如 ROLE_USER, ROLE_ADMIN)，提供名稱對齊功能。
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
    /**
     * 根據角色名稱尋找角色
     * 在為新使用者分配預設權限時，獲取對應的角色實體。
     * @param name 角色名稱 (如 "USER", "ADMIN")
     * @return 包含角色實體的 Optional 物件
     */
    Optional<Role> findByName(String name);
}
