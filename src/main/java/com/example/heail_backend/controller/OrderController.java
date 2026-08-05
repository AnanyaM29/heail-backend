package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AcceptAgreementRequest;
import com.example.heail_backend.dto.CapturePaypalOrderRequest;
import com.example.heail_backend.dto.OrderDetailsRequest;
import com.example.heail_backend.dto.OrderResponse;
import com.example.heail_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leader/orders")
@RequiredArgsConstructor
// Was hasRole('LEADER') — see OrgOrderController for why: single-role-column
// users can otherwise get locked out of a product they legitimately hold.
// Ownership is enforced in OrderService via requireOwnedOrder(id, email).
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrGet(Authentication auth) {
        return ResponseEntity.ok(orderService.getOrCreateDraftOrder(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orderService.getOrder(id, auth.getName()));
    }

    @PostMapping("/{id}/details")
    public ResponseEntity<OrderResponse> updateDetails(@PathVariable UUID id,
                                                         @RequestBody OrderDetailsRequest req,
                                                         Authentication auth) {
        return ResponseEntity.ok(orderService.updateDetails(id, auth.getName(), req.getDesignation(), req.getOrganisationName()));
    }

    @PostMapping("/{id}/agreement")
    public ResponseEntity<OrderResponse> acceptAgreement(@PathVariable UUID id,
                                                           @Valid @RequestBody AcceptAgreementRequest req,
                                                           Authentication auth) {
        return ResponseEntity.ok(orderService.acceptAgreement(id, auth.getName(), req.getVersion()));
    }

    @PostMapping("/{id}/create-paypal-order")
    public ResponseEntity<OrderResponse> createPaypalOrder(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orderService.createPaypalOrder(id, auth.getName()));
    }

    @PostMapping("/{id}/capture-paypal-order")
    public ResponseEntity<OrderResponse> capturePaypalOrder(@PathVariable UUID id,
                                                              @Valid @RequestBody CapturePaypalOrderRequest req,
                                                              Authentication auth) {
        return ResponseEntity.ok(orderService.capturePaypalOrder(id, auth.getName(), req.getPaypalOrderId()));
    }
}
