package com.example.heail_backend.repository;

import com.example.heail_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByOrderByCreatedAtAsc();

    /**
     * Paged + searchable roster for the admin Users tab — pushes both the
     * filtering and the LIMIT/OFFSET down to Postgres instead of loading every
     * non-deleted user into memory and filtering/slicing in Java.
     *
     * "JOIN FETCH" (not just "JOIN") on organisation matters here: a plain join
     * is only used to filter in the WHERE clause and leaves User.organisation
     * as an unfetched lazy proxy, so toUserDto()'s org.getName() would fire one
     * extra SELECT per row with an organisation — an N+1 that's cheap on a
     * local DB but costs a full round trip per row against RDS. FETCH pulls
     * the organisation back in the same query instead. Safe to combine with
     * Pageable here because this is a @ManyToOne (to-one) association — the
     * classic "fetch join breaks LIMIT/OFFSET" problem only applies to
     * fetch-joining a @OneToMany collection, which this isn't.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organisation o WHERE u.deletedAt IS NULL AND (" +
           ":q = '' OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.role) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<User> searchActive(@Param("q") String q, Pageable pageable);

    /** Same idea for the admin Logins tab — filtered by last-login recency instead of soft-delete. */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organisation o WHERE u.lastLoginAt > :cutoff AND (" +
           ":q = '' OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.role) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<User> searchLogins(@Param("cutoff") LocalDateTime cutoff, @Param("q") String q, Pageable pageable);
}
