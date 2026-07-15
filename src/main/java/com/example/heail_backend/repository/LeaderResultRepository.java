package com.example.heail_backend.repository;

import com.example.heail_backend.entity.LeaderResult;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaderResultRepository extends JpaRepository<LeaderResult, UUID> {
    List<LeaderResult> findByUserOrderByCreatedAtDesc(User user);
}
