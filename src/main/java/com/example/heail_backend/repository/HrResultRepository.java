package com.example.heail_backend.repository;

import com.example.heail_backend.entity.HrResult;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HrResultRepository extends JpaRepository<HrResult, UUID> {
    List<HrResult> findByUserOrderByCreatedAtDesc(User user);
}
