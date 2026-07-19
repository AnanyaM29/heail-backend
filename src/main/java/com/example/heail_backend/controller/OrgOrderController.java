package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AcceptAgreementRequest;
import com.example.heail_backend.dto.EmployeeRowRequest;
import com.example.heail_backend.dto.OrgMonitorResponse;
import com.example.heail_backend.dto.OrgOrderResponse;
import com.example.heail_backend.dto.OrgReportResponse;
import com.example.heail_backend.service.OrgOrderService;
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
@PreAuthorize("hasRole('ORG_ADMIN')")
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

    @PostMapping("/{id}/agreement")
    public ResponseEntity<OrgOrderResponse> acceptAgreement(@PathVariable UUID id,
                                                              @Valid @RequestBody AcceptAgreementRequest req,
                                                              Authentication auth) {
        return ResponseEntity.ok(orgOrderService.acceptAgreement(id, auth.getName(), req.getVersion()));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<OrgOrderResponse> pay(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(orgOrderService.payMock(id, auth.getName()));
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
