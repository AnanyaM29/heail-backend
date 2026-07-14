package com.example.heail_backend.repository;

import com.example.heail_backend.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {
    Optional<OtpToken> findTopByEmailOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("UPDATE OtpToken o SET o.used = true WHERE o.email = :email")
    void invalidateAllByEmail(String email);
}
