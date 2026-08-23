package com.example.heail_backend.service;

import com.example.heail_backend.dto.AdminPartnerDto;
import com.example.heail_backend.dto.AdminPaymentDto;
import com.example.heail_backend.dto.AdminTestSessionDto;
import com.example.heail_backend.dto.AdminUserDto;
import com.example.heail_backend.dto.HrCandidateRequestDto;
import com.example.heail_backend.dto.OrgReportResponse;
import com.example.heail_backend.entity.AssessmentSession;
import com.example.heail_backend.entity.Entitlement;
import com.example.heail_backend.entity.EntitlementSource;
import com.example.heail_backend.entity.HrCandidate;
import com.example.heail_backend.entity.HrCandidateRequest;
import com.example.heail_backend.entity.LeaderResult;
import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrderStatus;
import com.example.heail_backend.entity.Organisation;
import com.example.heail_backend.entity.PartnerApplication;
import com.example.heail_backend.entity.User;
import com.example.heail_backend.repository.AssessmentSessionRepository;
import com.example.heail_backend.repository.EntitlementRepository;
import com.example.heail_backend.repository.HrCandidateRepository;
import com.example.heail_backend.repository.HrCandidateRequestRepository;
import com.example.heail_backend.repository.LeaderResultRepository;
import com.example.heail_backend.repository.OrderRepository;
import com.example.heail_backend.repository.PartnerApplicationRepository;
import com.example.heail_backend.repository.RefreshTokenRepository;
import com.example.heail_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int INVOICE_LOOKBACK_MONTHS = 24;

    private final AssessmentSessionRepository sessionRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final LeaderResultRepository leaderResultRepo;
    private final PartnerApplicationRepository partnerRepo;
    private final EmailService emailService;
    private final InvoiceService invoiceService;
    private final OrgReportService orgReportService;
    private final OrgReportPdfService orgReportPdfService;
    private final HrCandidateRequestRepository hrRequestRepo;
    private final HrCandidateRepository hrCandidateRepo;
    private final HrOrderService hrOrderService;
    private final EntitlementRepository entitlementRepo;

    @Transactional(readOnly = true)
    public List<AdminTestSessionDto> listTests(int months) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        return sessionRepo.findByStartedAtAfterOrderByStartedAtDesc(cutoff).stream()
                .map(this::toTestDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPaymentDto> listPayments(int months) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        return orderRepo.findByDraftAtAfterOrderByDraftAtDesc(cutoff).stream()
                .map(this::toPaymentDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers() {
        return userRepo.findAllByDeletedAtIsNullOrderByCreatedAtAsc().stream()
                .map(this::toUserDto).toList();
    }

    /* ── Blacklist / soft-delete ──────────────────────────────────── */
    @Transactional
    public void blacklistUser(UUID userId) {
        User user = requireUser(userId);
        user.setActive(false);
        user.setBlacklistedAt(LocalDateTime.now());
        userRepo.save(user);
        refreshTokenRepo.revokeAllByUserId(userId);
    }

    @Transactional
    public void unblacklistUser(UUID userId) {
        User user = requireUser(userId);
        user.setActive(true);
        user.setBlacklistedAt(null);
        userRepo.save(user);
    }

    @Transactional
    public void removeUser(UUID userId) {
        User user = requireUser(userId);
        user.setDeletedAt(LocalDateTime.now());
        user.setActive(false);
        userRepo.save(user);
        refreshTokenRepo.revokeAllByUserId(userId);
    }

    @Transactional
    public void restoreUser(UUID userId) {
        User user = requireUser(userId);
        user.setDeletedAt(null);
        user.setActive(true);
        userRepo.save(user);
    }

    /* ── Resend results: whichever kind of result this user actually has ── */
    @Transactional
    public void resendResults(UUID userId) {
        User user = requireUser(userId);

        List<LeaderResult> leaderResults = leaderResultRepo.findByUserOrderByCreatedAtDesc(user);
        if (!leaderResults.isEmpty()) {
            emailService.sendLeaderResultsReady(user.getEmail(), user.getName());
            return;
        }

        List<Order> releasedReports = orderRepo.findByUserAndReportReleasedAtIsNotNullOrderByReportReleasedAtDesc(user);
        if (!releasedReports.isEmpty()) {
            Order order = releasedReports.get(0);
            OrgReportResponse report = orgReportService.buildReport(order);
            byte[] reportPdf = orgReportPdfService.generate(report);
            emailService.sendOrgReportReleased(user.getEmail(), user.getName(), reportPdf);
            return;
        }

        throw new IllegalArgumentException("No results available to resend for this user");
    }

    /* ── Invoices: paid orders within the lookback window, and resending one ── */
    @Transactional(readOnly = true)
    public List<AdminPaymentDto> listInvoices(UUID userId) {
        User user = requireUser(userId);
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(INVOICE_LOOKBACK_MONTHS);
        return orderRepo.findByUserAndStatusAndPaidAtAfterOrderByPaidAtDesc(user, OrderStatus.PAID, cutoff).stream()
                .map(this::toPaymentDto).toList();
    }

    @Transactional
    public void resendInvoice(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != OrderStatus.PAID)
            throw new IllegalArgumentException("Only paid orders have an invoice to send");

        LocalDateTime cutoff = LocalDateTime.now().minusMonths(INVOICE_LOOKBACK_MONTHS);
        if (order.getPaidAt() == null || order.getPaidAt().isBefore(cutoff))
            throw new IllegalArgumentException("Invoices can only be resent for services taken in the last " + INVOICE_LOOKBACK_MONTHS + " months");

        User user = order.getUser();
        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String amountDisplay = order.getCurrency() + " " + total.setScale(2, RoundingMode.HALF_UP);
        byte[] invoicePdf = invoiceService.generate(order, user.getName(), user.getEmail());
        emailService.sendInvoice(user.getEmail(), user.getName(), amountDisplay, invoicePdf, order.getInvoiceNumber());
    }

    /* ── Payment reminders for orders not yet paid ────────────────── */
    @Transactional
    public void sendPaymentReminder(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        sendPaymentReminderForOrder(order);
    }

    @Transactional
    public void sendPaymentReminders(List<UUID> orderIds) {
        for (UUID orderId : orderIds) {
            orderRepo.findById(orderId).ifPresent(order -> {
                try {
                    sendPaymentReminderForOrder(order);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipped payment reminder for order {}: {}", orderId, e.getMessage());
                }
            });
        }
    }

    private void sendPaymentReminderForOrder(Order order) {
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.FAILED
                || order.getStatus() == OrderStatus.ABANDONED)
            throw new IllegalArgumentException("This order isn't awaiting payment");

        User user = order.getUser();
        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String amountDisplay = order.getCurrency() + " " + total.setScale(2, RoundingMode.HALF_UP);
        emailService.sendPaymentReminder(user.getEmail(), user.getName(), productDisplayName(order.getProductCode()), amountDisplay);
    }

    private String productDisplayName(String productCode) {
        return switch (productCode) {
            case "LEADER_CLASSIC" -> "The Gita Leader — Classic Assessment";
            case "SUITE_4PULSE" -> "Organisational Transformation Diagnostic — 4-Pulse Suite";
            default -> productCode;
        };
    }

    /* ── Partner applications ─────────────────────────────────────── */
    @Transactional(readOnly = true)
    public List<AdminPartnerDto> listPartners() {
        return partnerRepo.listSummaries();
    }

    /**
     * Reads the resume bytes out to a plain record while the transaction (and
     * therefore the JDBC connection the lob stream is tied to) is still open.
     * Returning the entity itself and letting the controller call
     * getResumeData() afterward throws "Unable to access lob stream" — Postgres
     * stores this byte[] as a large object (oid), which can only be streamed
     * within the transaction/connection that fetched it.
     *
     * Separately: a handful of older rows point at a large object that no
     * longer exists in pg_largeobject (likely lost in a dump/restore that
     * didn't carry blobs across) — Hibernate surfaces that as a
     * JpaSystemException("Unable to access lob stream") wrapping a Postgres
     * "large object ... does not exist" error. That's a missing file, not a
     * server error, so it's translated into the same "no resume on file"
     * response the frontend already handles instead of a raw 500.
     */
    @Transactional(readOnly = true)
    public PartnerResumeFile getPartnerResume(UUID id) {
        PartnerApplication application;
        byte[] data;
        try {
            application = partnerRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Partner application not found"));
            data = application.getResumeData();
        } catch (JpaSystemException e) {
            log.warn("Resume data unreadable for partner application {} (likely a missing large object): {}", id, e.getMessage());
            throw new IllegalArgumentException("This application's resume file is missing from storage");
        }
        if (data == null)
            throw new IllegalArgumentException("This application has no resume on file");

        String filename = application.getResumeFileName() != null ? application.getResumeFileName() : "resume";
        return new PartnerResumeFile(data, filename);
    }

    public record PartnerResumeFile(byte[] data, String filename) {}

    /* ── HR candidate reallocation/retake requests ─────────────────── */
    @Transactional(readOnly = true)
    public List<HrCandidateRequestDto> listHrRequests() {
        return hrRequestRepo.findByStatusOrderByCreatedAtAsc("PENDING").stream().map(this::toHrRequestDto).toList();
    }

    @Transactional
    public void approveHrRequest(UUID requestId, String reviewerEmail) {
        User reviewer = userRepo.findByEmail(reviewerEmail).orElse(null);
        HrCandidateRequest req = requirePendingRequest(requestId);
        HrCandidate candidate = req.getCandidate();
        Order order = req.getOrder();

        if ("REALLOCATION".equals(req.getType())) {
            candidate.setStatus("REALLOCATED");
            hrCandidateRepo.save(candidate);

            HrCandidate replacement = new HrCandidate();
            replacement.setOrder(order);
            replacement.setName(req.getNewName());
            replacement.setDob(req.getNewDob());
            replacement.setEmail(req.getNewEmail());
            replacement.setMobile(req.getNewMobile());
            replacement.setAssessmentStartDate(req.getNewStartDate());
            hrOrderService.fulfilOneCandidate(order, replacement,
                    hrOrderService.selectedPillarIds(order), hrOrderService.selectedPillarNames(order));

            emailService.sendBuyerReallocationApproved(order.getUser().getEmail(), order.getUser().getName(),
                    candidate.getName(), replacement.getName());
        } else { // RETAKE
            List<Short> pillarIds = hrOrderService.selectedPillarIds(order);
            for (Short assessmentId : pillarIds) {
                Entitlement entitlement = new Entitlement();
                entitlement.setUser(candidate.getUser());
                entitlement.setProductCode("HR_A" + assessmentId);
                entitlement.setSource(EntitlementSource.PURCHASE);
                entitlement.setOrder(order);
                entitlement.setUsed(false);
                entitlementRepo.save(entitlement);
            }
            String freshToken = hrOrderService.regenerateCandidateToken(candidate);
            emailService.sendCandidateRetakeGranted(candidate.getEmail(), candidate.getName(),
                    String.join(", ", hrOrderService.selectedPillarNames(order)), freshToken);
        }

        req.setStatus("APPROVED");
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        hrRequestRepo.save(req);
    }

    @Transactional
    public void rejectHrRequest(UUID requestId, String reviewerEmail, String note) {
        User reviewer = userRepo.findByEmail(reviewerEmail).orElse(null);
        HrCandidateRequest req = requirePendingRequest(requestId);
        req.setStatus("REJECTED");
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        req.setReviewNote(note);
        hrRequestRepo.save(req);
    }

    private HrCandidateRequest requirePendingRequest(UUID requestId) {
        HrCandidateRequest req = hrRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus()))
            throw new IllegalArgumentException("This request has already been reviewed");
        return req;
    }

    private HrCandidateRequestDto toHrRequestDto(HrCandidateRequest req) {
        HrCandidateRequestDto dto = new HrCandidateRequestDto();
        dto.setId(req.getId());
        dto.setType(req.getType());
        dto.setStatus(req.getStatus());
        dto.setOrderId(req.getOrder().getId());
        dto.setBuyerName(req.getOrder().getUser().getName());
        dto.setBuyerEmail(req.getOrder().getUser().getEmail());
        dto.setCandidateId(req.getCandidate().getId());
        dto.setCandidateName(req.getCandidate().getName());
        dto.setCandidateEmail(req.getCandidate().getEmail());
        dto.setNewName(req.getNewName());
        dto.setNewDob(req.getNewDob());
        dto.setNewEmail(req.getNewEmail());
        dto.setNewMobile(req.getNewMobile());
        dto.setNewStartDate(req.getNewStartDate());
        dto.setCreatedAt(req.getCreatedAt());
        return dto;
    }

    private User requireUser(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listLogins(int months) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        return userRepo.findByLastLoginAtAfterOrderByLastLoginAtDesc(cutoff).stream()
                .map(this::toUserDto).toList();
    }

    private AdminTestSessionDto toTestDto(AssessmentSession session) {
        User user = session.getUser();
        Organisation org = user.getOrganisation();

        AdminTestSessionDto dto = new AdminTestSessionDto();
        dto.setId(session.getId());
        dto.setUserName(user.getName());
        dto.setUserEmail(user.getEmail());
        dto.setOrganisationName(org != null ? org.getName() : null);
        dto.setProductCode(session.getProductCode());
        dto.setPulse(session.getPulse());
        dto.setStatus(session.getStatus().name());
        dto.setAttemptNumber(session.getAttemptNumber());
        dto.setStartedAt(session.getStartedAt());
        dto.setCompletedAt(session.getCompletedAt());
        return dto;
    }

    private AdminPaymentDto toPaymentDto(Order order) {
        User user = order.getUser();
        BigDecimal total = order.getAmount().add(order.getGstAmount());

        AdminPaymentDto dto = new AdminPaymentDto();
        dto.setId(order.getId());
        dto.setUserName(user.getName());
        dto.setUserEmail(user.getEmail());
        dto.setProductCode(order.getProductCode());
        dto.setStatus(order.getStatus().name());
        dto.setCurrency(order.getCurrency());
        dto.setBaseAmount(order.getAmount());
        dto.setGstAmount(order.getGstAmount());
        dto.setTotalAmount(total);
        dto.setGatewayOrderRef(order.getGatewayOrderRef());
        dto.setInvoiceNumber(order.getInvoiceNumber());
        dto.setDraftAt(order.getDraftAt());
        dto.setPaidAt(order.getPaidAt());
        return dto;
    }

    private AdminUserDto toUserDto(User user) {
        Organisation org = user.getOrganisation();

        AdminUserDto dto = new AdminUserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setCity(user.getCity());
        dto.setCountry(user.getCountry());
        dto.setOrganisationName(org != null ? org.getName() : null);
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setActive(user.isActive());
        dto.setBlacklistedAt(user.getBlacklistedAt());
        dto.setDeletedAt(user.getDeletedAt());
        return dto;
    }
}
