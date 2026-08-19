package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AdminPartnerDto;
import com.example.heail_backend.dto.AdminPaymentDto;
import com.example.heail_backend.dto.AdminTestSessionDto;
import com.example.heail_backend.dto.AdminUserDto;
import com.example.heail_backend.entity.PartnerApplication;
import com.example.heail_backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Superadmin-only reporting and moderation views — test activity, payments,
 * user/login rosters, blacklist/removal, results/invoice resends, and partner
 * applications. Reset-any-account lives on the existing public forgot-password
 * endpoint (the admin console just calls it directly with an arbitrary email),
 * so it isn't duplicated here.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/tests")
    public ResponseEntity<List<AdminTestSessionDto>> tests(@RequestParam(defaultValue = "3") int months) {
        return ResponseEntity.ok(adminDashboardService.listTests(months));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<AdminPaymentDto>> payments(@RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(adminDashboardService.listPayments(months));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> users() {
        return ResponseEntity.ok(adminDashboardService.listUsers());
    }

    @GetMapping("/logins")
    public ResponseEntity<List<AdminUserDto>> logins(@RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(adminDashboardService.listLogins(months));
    }

    @PatchMapping("/users/{id}/blacklist")
    public ResponseEntity<?> blacklistUser(@PathVariable UUID id) {
        adminDashboardService.blacklistUser(id);
        return ResponseEntity.ok(Map.of("message", "User blacklisted"));
    }

    @PatchMapping("/users/{id}/unblacklist")
    public ResponseEntity<?> unblacklistUser(@PathVariable UUID id) {
        adminDashboardService.unblacklistUser(id);
        return ResponseEntity.ok(Map.of("message", "User unblacklisted"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> removeUser(@PathVariable UUID id) {
        adminDashboardService.removeUser(id);
        return ResponseEntity.ok(Map.of("message", "User removed"));
    }

    @PatchMapping("/users/{id}/restore")
    public ResponseEntity<?> restoreUser(@PathVariable UUID id) {
        adminDashboardService.restoreUser(id);
        return ResponseEntity.ok(Map.of("message", "User restored"));
    }

    @PostMapping("/users/{id}/resend-results")
    public ResponseEntity<?> resendResults(@PathVariable UUID id) {
        adminDashboardService.resendResults(id);
        return ResponseEntity.ok(Map.of("message", "Results resent"));
    }

    @GetMapping("/users/{id}/invoices")
    public ResponseEntity<List<AdminPaymentDto>> invoices(@PathVariable UUID id) {
        return ResponseEntity.ok(adminDashboardService.listInvoices(id));
    }

    @PostMapping("/orders/{orderId}/resend-invoice")
    public ResponseEntity<?> resendInvoice(@PathVariable UUID orderId) {
        adminDashboardService.resendInvoice(orderId);
        return ResponseEntity.ok(Map.of("message", "Invoice resent"));
    }

    @PostMapping("/orders/{orderId}/send-payment-reminder")
    public ResponseEntity<?> sendPaymentReminder(@PathVariable UUID orderId) {
        adminDashboardService.sendPaymentReminder(orderId);
        return ResponseEntity.ok(Map.of("message", "Reminder sent"));
    }

    @PostMapping("/orders/send-payment-reminders")
    public ResponseEntity<?> sendPaymentReminders(@RequestBody List<UUID> orderIds) {
        adminDashboardService.sendPaymentReminders(orderIds);
        return ResponseEntity.ok(Map.of("message", "Reminders sent"));
    }

    @GetMapping("/partners")
    public ResponseEntity<List<AdminPartnerDto>> partners() {
        return ResponseEntity.ok(adminDashboardService.listPartners());
    }

    @GetMapping("/partners/{id}/resume")
    public ResponseEntity<byte[]> partnerResume(@PathVariable UUID id) {
        PartnerApplication application = adminDashboardService.getPartnerResume(id);
        String filename = application.getResumeFileName() != null ? application.getResumeFileName() : "resume";
        String contentType = URLConnection.guessContentTypeFromName(filename);

        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(application.getResumeData());
    }
}
