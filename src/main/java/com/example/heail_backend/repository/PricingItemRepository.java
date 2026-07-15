package com.example.heail_backend.repository;

import com.example.heail_backend.entity.PricingItem;
import com.example.heail_backend.entity.PricingItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingItemRepository extends JpaRepository<PricingItem, PricingItemId> {
    Optional<PricingItem> findByProductCodeAndCurrencyAndActiveTrue(String productCode, String currency);
}
