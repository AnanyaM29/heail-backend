package com.example.heail_backend.repository;

import com.example.heail_backend.entity.PartnerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, UUID> {
}
