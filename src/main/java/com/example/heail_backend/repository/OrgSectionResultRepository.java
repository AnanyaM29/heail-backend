package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrgSectionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgSectionResultRepository extends JpaRepository<OrgSectionResult, java.util.UUID> {
    List<OrgSectionResult> findByOrder(Order order);
    void deleteByOrder(Order order);
}
