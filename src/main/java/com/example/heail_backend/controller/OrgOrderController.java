package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AcceptAgreementRequest;
import com.example.heail_backend.dto.CreateOrderRequest;
import com.example.heail_backend.dto.EmployeeRowRequest;
import com.example.heail_backend.dto.OrgMonitorResponse;
import com.example.heail_backend.dto.OrgOrderResponse;
import com.example.heail_backend.dto.OrgReportResponse;
import com.example.heail_backend.dto.SetOrgDetailsRequest;
import com.example.heail_backend.dto.VerifyRazorpayPaymentRequest;
import com.example.heail_backend.service.OrgOrderService;
import io.micrometer.common.lang.Nullable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/orders")
@RequiredArgsConstructor
// Was hasRole('ORG_ADMIN') — but User.role is single-valued, so a person who is
// also a pulse respondent (or a Leader customer) elsewhere would get 403'd here
// even though they legitimately own this order. Real ownership is already
// enforced in OrgOrderService via requireOwnedOrder(id, email), so this only
// needs to confirm the caller is logged in.
@PreAuthorize("isAuthenticated()")
public class OrgOrderController {

    private final OrgOrderService orgOrderService;

    @PostMapping
    public ResponseEntity<OrgOrderResponse> createOrGet(@RequestBody(required = false) CreateOrderRequest req, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.getOrCreateDraftOrder(auth.getName(), req == null ? null : req.getCurrency()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrgOrderResponse> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.getOrder(id, auth.getName()));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<OrgOrderResponse>> mine(Authentication auth) {
        return ResponseEntity.ok(orgOrderService.listMyOrders(auth.getName()));
    }

    @PutMapping("/{id}/employees")
    public ResponseEntity<OrgOrderResponse> setEmployees(@PathVariable UUID id,
                                                           @RequestBody List<EmployeeRowRequest> rows,
                                                           Authentication auth) {
        return ResponseEntity.ok(orgOrderService.setEmployees(id, auth.getName(), rows));
    }

    @PutMapping("/{id}/organisation")
    public ResponseEntity<OrgOrderResponse> setOrgDetails(@PathVariable UUID id,
                                                             @Valid @RequestBody SetOrgDetailsRequest req,
                                                             Authentication auth) {
        return ResponseEntity.ok(orgOrderService.setOrgDetails(id, auth.getName(),
                req.getOrganisationName(), req.getHeadcount(), req.getIndustry()));
    }

    @PostMapping("/{id}/agreement")
    public ResponseEntity<OrgOrderResponse> acceptAgreement(@PathVariable UUID id,
                                                              @Valid @RequestBody AcceptAgreementRequest req,
                                                              Authentication auth) {
        return ResponseEntity.ok(orgOrderService.acceptAgreement(id, auth.getName(), req.getVersion()));
    }

    // Payment endpoints are ORG_ADMIN/SUPERADMIN-only (unlike the rest of this
    // controller) — by the time a round reaches payment, setOrgDetails() has
    // already promoted the account from LEADER to ORG_ADMIN, so this doesn't
    // block onboarding; it just stops a non-org-admin account (e.g. an
    // EMPLOYEE respondent) from paying for a round it doesn't own. SUPERADMIN
    // is included so it can walk a round through to completion on a client's
    // behalf (e.g. manual/offline payment, since Razorpay is disabled by default).
    @PostMapping("/{id}/create-razorpay-order")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<OrgOrderResponse> createRazorpayOrder(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.createRazorpayOrder(id, auth.getName()));
    }

    @PostMapping("/{id}/verify-razorpay-payment")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<OrgOrderResponse> verifyRazorpayPayment(@PathVariable UUID id,
                                                                     @Valid @RequestBody VerifyRazorpayPaymentRequest req,
                                                                     Authentication auth) {
        return ResponseEntity.ok(orgOrderService.verifyRazorpayPayment(id, auth.getName(),
                req.getRazorpayOrderId(), req.getRazorpayPaymentId(), req.getRazorpaySignature()));
    }

    @PostMapping("/{id}/force-complete-test-payment")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<OrgOrderResponse> forceCompleteTestPayment(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.forceCompleteTestPayment(id, auth.getName()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrgOrderResponse> cancel(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.cancelOrder(id, auth.getName()));
    }

    @GetMapping("/{id}/monitor")
    public ResponseEntity<OrgMonitorResponse> monitor(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.getMonitor(id, auth.getName()));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<OrgReportResponse> report(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.getReport(id, auth.getName()));
    }
}
