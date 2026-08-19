package com.example.heail_backend.repository;

import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByOrderByCreatedAtAsc();
    List<User> findAllByDeletedAtIsNullOrderByCreatedAtAsc();
    List<User> findByLastLoginAtAfterOrderByLastLoginAtDesc(LocalDateTime cutoff);
}
