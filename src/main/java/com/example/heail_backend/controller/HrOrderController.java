package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AcceptAgreementRequest;
import com.example.heail_backend.dto.HrCreateOrderRequest;
import com.example.heail_backend.dto.OrderResponse;
import com.example.heail_backend.dto.VerifyRazorpayPaymentRequest;
import com.example.heail_backend.service.HrOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/orders")
@RequiredArgsConstructor
// Ownership enforced in HrOrderService via requireOwnedOrder(id, email) — same
// pattern as OrderController/OrgOrderController.
@PreAuthorize("isAuthenticated()")
public class HrOrderController {

    private final HrOrderService hrOrderService;

    @PostMapping
    public ResponseEntity<OrderResponse> selectAssessments(@Valid @RequestBody HrCreateOrderRequest req, Authentication auth) {
        return ResponseEntity.ok(hrOrderService.selectAssessments(auth.getName(), req.getAssessmentIds()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(hrOrderService.getOrder(id, auth.getName()));
    }

    @PostMapping("/{id}/agreement")
    public ResponseEntity<OrderResponse> acceptAgreement(@PathVariable UUID id,
                                                           @Valid @RequestBody AcceptAgreementRequest req,
                                                           Authentication auth) {
        return ResponseEntity.ok(hrOrderService.acceptAgreement(id, auth.getName(), req.getVersion()));
    }

    @PostMapping("/{id}/create-razorpay-order")
    public ResponseEntity<OrderResponse> createRazorpayOrder(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(hrOrderService.createRazorpayOrder(id, auth.getName()));
    }

    @PostMapping("/{id}/verify-razorpay-payment")
    public ResponseEntity<OrderResponse> verifyRazorpayPayment(@PathVariable UUID id,
                                                                 @Valid @RequestBody VerifyRazorpayPaymentRequest req,
                                                                 Authentication auth) {
        return ResponseEntity.ok(hrOrderService.verifyRazorpayPayment(id, auth.getName(),
                req.getRazorpayOrderId(), req.getRazorpayPaymentId(), req.getRazorpaySignature()));
    }

    @PostMapping("/{id}/force-complete-test-payment")
    public ResponseEntity<OrderResponse> forceCompleteTestPayment(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(hrOrderService.forceCompleteTestPayment(id, auth.getName()));
    }
}
