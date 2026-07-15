package com.example.heail_backend.repository;

import com.example.heail_backend.entity.ConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsentLogRepository extends JpaRepository<ConsentLog, UUID> {
}
