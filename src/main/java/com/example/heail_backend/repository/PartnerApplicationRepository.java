package com.example.heail_backend.repository;

import com.example.heail_backend.dto.AdminPartnerDto;
import com.example.heail_backend.entity.PartnerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, UUID> {
    List<PartnerApplication> findAllByOrderByCreatedAtDesc();

    /**
     * Deliberately never selects resume_data — that column is a Postgres large
     * object (see PartnerApplication.resumeData), and a handful of rows point
     * at large objects that no longer exist. Loading the full entity for a
     * list view (even just to check "is it null") eagerly hydrates that lob
     * and throws JpaSystemException: Unable to access lob stream for those
     * rows, taking down the whole list. This projects only the plain columns
     * the admin list actually needs, so a broken lob can never affect it.
     */
    @Query("select new com.example.heail_backend.dto.AdminPartnerDto(" +
            "a.id, a.name, a.country, a.city, a.mobile, a.email, a.consentGiven, " +
            "a.resumeFileName, (case when a.resumeFileName is not null then true else false end), a.createdAt) " +
            "from PartnerApplication a order by a.createdAt desc")
    List<AdminPartnerDto> listSummaries();
}
