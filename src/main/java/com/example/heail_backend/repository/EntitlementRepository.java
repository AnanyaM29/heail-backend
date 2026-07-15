package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Entitlement;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {
    Optional<Entitlement> findFirstByUserAndProductCodeAndUsedFalseOrderByCreatedAtAsc(User user, String productCode);
}
