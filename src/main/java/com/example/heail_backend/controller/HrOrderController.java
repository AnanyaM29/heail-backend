package com.example.heail_backend.controller;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.service.HrOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/{id}/candidates")
    public ResponseEntity<HrOrderResponse> getWithCandidates(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(hrOrderService.getOrderWithCandidates(id, auth.getName()));
    }

    @PutMapping("/{id}/candidates")
    public ResponseEntity<HrOrderResponse> setCandidates(@PathVariable UUID id,
                                                          @RequestBody List<CandidateRowRequest> rows,
                                                          Authentication auth) {
        return ResponseEntity.ok(hrOrderService.setCandidates(id, auth.getName(), rows));
    }

    @GetMapping("/candidates/mine")
    public ResponseEntity<List<HrCandidateDto>> myCandidates(Authentication auth) {
        return ResponseEntity.ok(hrOrderService.listMyCandidates(auth.getName()));
    }

    /** Returns a fresh DRAFT order with the same candidate already on it —
     *  frontend routes straight into /pricing/buy-hr/{id} (agreement → pay),
     *  same as any other order. Reallocation to a *different* person is
     *  deliberately not offered: an assessment is tied to the one person the
     *  buyer registered. */
    @PostMapping("/candidates/{candidateId}/retake")
    public ResponseEntity<OrderResponse> createRetakeOrder(@PathVariable UUID candidateId, Authentication auth) {
        return ResponseEntity.ok(hrOrderService.createRetakeOrder(candidateId, auth.getName()));
    }

    @PostMapping("/{id}/agreement")
    public ResponseEntity<OrderResponse> acceptAgreement(@PathVariable UUID id,
                                                           @Valid @RequestBody AcceptAgreementRequest req,
                                                           Authentication auth) {
        return ResponseEntity.ok(hrOrderService.acceptAgreement(id, auth.getName(), req.getVersion()));
    }

    @PostMapping("/{id}/apply-coupon")
    public ResponseEntity<OrderResponse> applyCoupon(@PathVariable UUID id,
                                                       @Valid @RequestBody ApplyCouponRequest req,
                                                       Authentication auth) {
        return ResponseEntity.ok(hrOrderService.applyCoupon(id, auth.getName(), req.getCode()));
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
