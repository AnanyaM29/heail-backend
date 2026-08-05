package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrderStatus;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserAndProductCodeOrderByDraftAtDesc(User user, String productCode);
    List<Order> findByStatusAndProductCode(OrderStatus status, String productCode);
    Optional<Order> findByGatewayOrderRef(String gatewayOrderRef);
}
