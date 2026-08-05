package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AcceptAgreementRequest;
import com.example.heail_backend.dto.CapturePaypalOrderRequest;
import com.example.heail_backend.dto.EmployeeRowRequest;
import com.example.heail_backend.dto.OrgMonitorResponse;
import com.example.heail_backend.dto.OrgOrderResponse;
import com.example.heail_backend.dto.OrgReportResponse;
import com.example.heail_backend.dto.SetOrgDetailsRequest;
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
    public ResponseEntity<OrgOrderResponse> createOrGet(Authentication auth) {
        return ResponseEntity.ok(orgOrderService.getOrCreateDraftOrder(auth.getName()));
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

    @PostMapping("/{id}/create-paypal-order")
    public ResponseEntity<OrgOrderResponse> createPaypalOrder(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.createPaypalOrder(id, auth.getName()));
    }

    @PostMapping("/{id}/capture-paypal-order")
    public ResponseEntity<OrgOrderResponse> capturePaypalOrder(@PathVariable UUID id,
                                                                 @Valid @RequestBody CapturePaypalOrderRequest req,
                                                                 Authentication auth) {
        return ResponseEntity.ok(orgOrderService.capturePaypalOrder(id, auth.getName(), req.getPaypalOrderId()));
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
