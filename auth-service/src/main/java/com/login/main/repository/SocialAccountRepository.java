package com.login.main.repository;

import com.login.main.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    @Query("SELECT s FROM SocialAccount s JOIN FETCH s.user WHERE s.providerID = :providerId")
    Optional<SocialAccount> findByProviderID(@Param("providerId") String providerId);
}
