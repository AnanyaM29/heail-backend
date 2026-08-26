package com.example.heail_backend.controller;

import com.example.heail_backend.dto.DiscountCouponDto;
import com.example.heail_backend.dto.GenerateCouponRequest;
import com.example.heail_backend.service.DiscountCouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class DiscountCouponAdminController {

    private final DiscountCouponService couponService;

    @PostMapping
    public ResponseEntity<DiscountCouponDto> generate(@Valid @RequestBody GenerateCouponRequest req, Authentication auth) {
        return ResponseEntity.ok(couponService.generate(req.getDiscountPercent(), auth.getName(), req.getEmail()));
    }

    @GetMapping
    public ResponseEntity<List<DiscountCouponDto>> list() {
        return ResponseEntity.ok(couponService.list());
    }

    @PostMapping("/{code}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable String code) {
        couponService.revoke(code);
        return ResponseEntity.ok().build();
    }
}
