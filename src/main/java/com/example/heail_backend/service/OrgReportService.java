package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgReportService {

    private static final List<String> PULSE_SEQUENCE =
            List.of("LEADER_PULSE", "TALENT_PULSE", "SYSTEM_PULSE", "GROWTH_PULSE");

    private static final int MIN_RESPONDENTS = 5;
    private static final int PARTIAL_THRESHOLD_DAY = 8;
    private static final double PARTIAL_THRESHOLD_PCT = 0.80;

    private final OrderEmployeeRepository orderEmployeeRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final OrderRepository orderRepo;
    private final EmailService emailService;
    private final ScoringService scoringService;
    private final OrgReportPdfService orgReportPdfService;

    /* ── Called after every Pulse submission and by the scheduled sweep. Scores are
       computed for fully-completed employees only, and released the moment either
       100% of employees have completed all 4 Pulses (any day), or it is day 8+ and
       at least 80% (min 5) have completed. No approval gate — released straight to
       the org admin who created the round. Idempotent via reportReleasedAt. ────── */
    @Transactional
    public void checkAndMaybeCompute(Order order) {
        if (order == null || order.getReportReleasedAt() != null) return;

        List<OrderEmployee> employees = orderEmployeeRepo.findByOrder(order);
        if (employees.isEmpty()) return;

        List<OrderEmployee> fullyCompleted = employees.stream()
                .filter(this::hasCompletedAllPulses).toList();

        int total = employees.size();
        int done = fullyCompleted.size();
        boolean allDone = done == total;
        boolean day8OrLater = order.getPaidAt() != null
                && !LocalDateTime.now().isBefore(order.getPaidAt().plusDays(PARTIAL_THRESHOLD_DAY));
        boolean partialEligible = day8OrLater && done >= MIN_RESPONDENTS && done >= total * PARTIAL_THRESHOLD_PCT;

        if (!allDone && !partialEligible) return;

        computeAndRelease(order, fullyCompleted);
    }

    private boolean hasCompletedAllPulses(OrderEmployee emp) {
        User user = emp.getUser();
        if (user == null) return false;
        return PULSE_SEQUENCE.stream().allMatch(p ->
                sessionRepo.findByUserAndOrderAndPulse(user, emp.getOrder(), p).stream()
                        .anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED));
    }

    // The index report (see ScoringService) is computed live from raw response rows every
    // time it's requested, not persisted as a snapshot — this just flips the release flag
    // (idempotent gate for checkAndMaybeCompute) and sends the notification.
    private void computeAndRelease(Order order, List<OrderEmployee> fullyCompleted) {
        order.setReportReleasedAt(LocalDateTime.now());
        orderRepo.save(order);

        log.info("Report released for order {} ({} of {} employees fully completed)",
                order.getId(), fullyCompleted.size(), orderEmployeeRepo.findByOrder(order).size());

        OrgReportResponse report = scoringService.computeReport(order, fullyCompleted);
        byte[] reportPdf = orgReportPdfService.generate(report);
        emailService.sendOrgReportReleased(order.getUser().getEmail(), order.getUser().getName(), reportPdf);
    }

    /* ── Employee×Pulse progress grid, never any scores ────────────── */
    @Transactional(readOnly = true)
    public OrgMonitorResponse buildMonitor(Order order) {
        List<OrderEmployee> employees = orderEmployeeRepo.findByOrder(order);
        List<EmployeeProgressDto> dtos = new ArrayList<>();
        int fullyCompletedCount = 0;

        for (OrderEmployee emp : employees) {
            EmployeeProgressDto dto = new EmployeeProgressDto();
            dto.setId(emp.getId());
            dto.setName(emp.getName());
            dto.setEmail(emp.getEmail());
            dto.setInvitationStatus(emp.getInvitationStatus());

            Map<String, String> states = new LinkedHashMap<>();
            boolean allDone = emp.getUser() != null;
            for (String pulse : PULSE_SEQUENCE) {
                String state = "NOT_STARTED";
                if (emp.getUser() != null) {
                    List<AssessmentSession> sessions = sessionRepo.findByUserAndOrderAndPulse(emp.getUser(), order, pulse);
                    boolean completed = sessions.stream().anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED);
                    boolean inProgress = sessions.stream().anyMatch(s -> s.getStatus() == SessionStatus.IN_PROGRESS);
                    state = completed ? "COMPLETED" : inProgress ? "IN_PROGRESS" : "NOT_STARTED";
                    if (!completed) allDone = false;
                }
                states.put(pulse, state);
            }
            dto.setPulseStates(states);
            dto.setAllCompleted(allDone);
            if (allDone) fullyCompletedCount++;
            dtos.add(dto);
        }

        OrgMonitorResponse res = new OrgMonitorResponse();
        res.setOrderId(order.getId());
        res.setTotalEmployees(employees.size());
        res.setFullyCompletedCount(fullyCompletedCount);
        res.setReportReleasedAt(order.getReportReleasedAt());
        res.setEmployees(dtos);
        return res;
    }

    /* ── Full index report: Position, Divergence, Structure, Risk, Voice — see
       ScoringService and heail-index-architecture.md. Computed live from raw
       response rows on every call, not from a persisted snapshot. ────────── */
    @Transactional(readOnly = true)
    public OrgReportResponse buildReport(Order order) {
        if (order.getReportReleasedAt() == null)
            throw new IllegalStateException("Report has not been released yet for this round");

        List<OrderEmployee> fullyCompleted = orderEmployeeRepo.findByOrder(order).stream()
                .filter(this::hasCompletedAllPulses).toList();

        return scoringService.computeReport(order, fullyCompleted);
    }
}
