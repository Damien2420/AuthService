package com.login.main.repository;

import com.login.main.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用者資料儲存庫
 * 提供對 User 實體的資料存取介面，支援透過使用者名稱與電子郵件進行關鍵字搜尋。
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * 根據使用者名稱尋找使用者
     * 在登入或註冊校驗時，根據唯一帳號獲取使用者實體。
     * @param username 使用者名稱
     * @return 包含使用者實體的 Optional 物件
     */
    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * 根據電子郵件尋找使用者
     * 在註冊校驗或社交登入關聯時，根據唯一信箱獲取使用者實體。
     * @param email 電子郵件
     * @return 包含使用者實體的 Optional 物件
     */
    Optional<User> findByEmail(String email);

    /**
     * 根據使用者名稱尋找使用者（含角色與社交帳號）
     * 用於個人資料頁面，一次性載入角色與社交帳號，避免 N+1 查詢。
     * @param username 使用者名稱
     * @return 包含使用者實體的 Optional 物件
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.socialAccounts WHERE u.username = :username")
    Optional<User> findByUsernameWithSocialAccounts(@Param("username") String username);
}
