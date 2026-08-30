package com.example.heail_backend.repository;

import com.example.heail_backend.dto.AdminPartnerDto;
import com.example.heail_backend.entity.PartnerApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, UUID> {
    List<PartnerApplication> findAllByOrderByCreatedAtDesc();

    /**
     * Paged + searchable list for the admin Partners tab. Deliberately never
     * selects resume_data — that column is a Postgres large object (see
     * PartnerApplication.resumeData), and a handful of rows point at large
     * objects that no longer exist. Loading the full entity for a list view
     * (even just to check "is it null") eagerly hydrates that lob and throws
     * JpaSystemException: Unable to access lob stream for those rows, taking
     * down the whole list. This projects only the plain columns the admin
     * list actually needs, so a broken lob can never affect it.
     *
     * The count query is given explicitly because Spring Data can't reliably
     * derive one from a constructor-expression (`select new ...`) select.
     */
    @Query(value = "select new com.example.heail_backend.dto.AdminPartnerDto(" +
            "a.id, a.name, a.country, a.city, a.mobile, a.email, a.consentGiven, " +
            "a.resumeFileName, (case when a.resumeFileName is not null then true else false end), a.createdAt) " +
            "from PartnerApplication a WHERE (" +
            ":q = '' OR " +
            "LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.city) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.country) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "order by a.createdAt desc",
           countQuery = "select count(a) from PartnerApplication a WHERE (" +
            ":q = '' OR " +
            "LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.city) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.country) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<AdminPartnerDto> search(@Param("q") String q, Pageable pageable);
}
