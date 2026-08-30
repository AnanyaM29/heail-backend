package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrderStatus;
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

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserAndProductCodeOrderByDraftAtDesc(User user, String productCode);
    List<Order> findByStatusAndProductCode(OrderStatus status, String productCode);
    Optional<Order> findByGatewayOrderRef(String gatewayOrderRef);
    List<Order> findByUserAndReportReleasedAtIsNotNullOrderByReportReleasedAtDesc(User user);
    List<Order> findByUserAndStatusAndPaidAtAfterOrderByPaidAtDesc(User user, OrderStatus status, LocalDateTime cutoff);

    /**
     * Paged + searchable feed for the admin Payments tab — a FETCH join, not a
     * plain one, since toPaymentDto() reads order.getUser() for every row (see
     * UserRepository.searchActive for the full N+1 explanation).
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.user u WHERE o.draftAt > :cutoff AND (" +
           ":q = '' OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(o.productCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(CAST(o.status AS string)) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Order> search(@Param("cutoff") LocalDateTime cutoff, @Param("q") String q, Pageable pageable);
}
