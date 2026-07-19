package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrderEmployee;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderEmployeeRepository extends JpaRepository<OrderEmployee, UUID> {
    List<OrderEmployee> findByOrder(Order order);
    void deleteByOrder(Order order);
    List<OrderEmployee> findByUser(User user);
}
