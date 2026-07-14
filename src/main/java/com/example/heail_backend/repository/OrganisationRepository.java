package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {
}
