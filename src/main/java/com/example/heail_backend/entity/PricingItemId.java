package com.example.heail_backend.entity;

import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingItemId implements Serializable {
    String productCode;
    String currency;
}
