package com.example.heail_backend.service;

import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/* ── Hourly sweep over open PAID SUITE_4PULSE rounds: rechecks the report trigger
   (in case the instant post-submit hook missed a tick, e.g. a restart) and fires
   employee reminders (24h not-started / 12h pending) and org-admin escalation
   emails (day 3 non-starters, day 5 detailed status, day 8 final notice). ────── */
@Slf4j
@Component
@RequiredArgsConstructor
public class PulseReminderScheduler {

    private static final String SUITE_4PULSE_PRODUCT = "SUITE_4PULSE";
    private static final List<String> PULSE_SEQUENCE =
            List.of("LEADER_PULSE", "TALENT_PULSE", "SYSTEM_PULSE", "GROWTH_PULSE");

    private static final Duration NOT_STARTED_CADENCE = Duration.ofHours(24);
    private static final Duration PENDING_CADENCE = Duration.ofHours(12);
    private static final Duration ORG_EMAIL_CADENCE = Duration.ofHours(24);
    private static final int NON_STARTER_DAY = 3;
    private static final int DETAILED_STATUS_DAY = 5;
    private static final int FINAL_DAY = 8;

    private final OrderRepository orderRepo;
    private final OrderEmployeeRepository orderEmployeeRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final EmailService emailService;
    private final OrgReportService orgReportService;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void sweep() {
        List<Order> openOrders = orderRepo.findByStatusAndProductCode(OrderStatus.PAID, SUITE_4PULSE_PRODUCT).stream()
                .filter(o -> o.getReportReleasedAt() == null)
                .toList();

        for (Order order : openOrders) {
            orgReportService.checkAndMaybeCompute(order);
            if (order.getReportReleasedAt() != null) continue;

            List<OrderEmployee> employees = orderEmployeeRepo.findByOrder(order);
            remindEmployees(order, employees);
            escalateToOrgAdmin(order, employees);
        }
    }

    private void remindEmployees(Order order, List<OrderEmployee> employees) {
        LocalDateTime now = LocalDateTime.now();
        for (OrderEmployee emp : employees) {
            if (emp.getUser() == null) continue;

            List<String> pendingPulses = new ArrayList<>();
            boolean anyStarted = false;
            for (String pulse : PULSE_SEQUENCE) {
                List<AssessmentSession> sessions = sessionRepo.findByUserAndOrderAndPulse(emp.getUser(), order, pulse);
                boolean completed = sessions.stream().anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED);
                if (!sessions.isEmpty()) anyStarted = true;
                if (!completed) pendingPulses.add(displayName(pulse));
            }
            if (pendingPulses.isEmpty()) continue;

            Duration cadence = anyStarted ? PENDING_CADENCE : NOT_STARTED_CADENCE;
            boolean due = emp.getLastReminderAt() == null
                    || Duration.between(emp.getLastReminderAt(), now).compareTo(cadence) >= 0;
            if (!due) continue;

            if (anyStarted) {
                emailService.sendEmployeeReminderPending(emp.getEmail(), emp.getName(), pendingPulses);
            } else {
                String orgName = order.getUser().getOrganisation() != null
                        ? order.getUser().getOrganisation().getName() : "your organisation";
                emailService.sendEmployeeReminderNotStarted(emp.getEmail(), emp.getName(), orgName);
            }
            emp.setLastReminderAt(now);
            orderEmployeeRepo.save(emp);
        }
    }

    private void escalateToOrgAdmin(Order order, List<OrderEmployee> employees) {
        if (order.getPaidAt() == null) return;
        LocalDateTime now = LocalDateTime.now();
        long daysSincePaid = Duration.between(order.getPaidAt(), now).toDays();

        int total = employees.size();
        int completed = (int) employees.stream().filter(e -> hasCompletedAllPulses(e, order)).count();

        if (daysSincePaid >= NON_STARTER_DAY && isDue(order.getLastNonStarterEmailAt(), now)) {
            List<String> nonStarters = employees.stream()
                    .filter(e -> !hasStartedAnyPulse(e, order))
                    .map(OrderEmployee::getName)
                    .toList();
            if (!nonStarters.isEmpty()) {
                emailService.sendOrgNonStarterList(order.getUser().getEmail(), order.getUser().getName(), nonStarters);
                order.setLastNonStarterEmailAt(now);
                orderRepo.save(order);
            }
        }

        if (daysSincePaid >= DETAILED_STATUS_DAY && isDue(order.getLastStatusEmailAt(), now)) {
            emailService.sendOrgDetailedStatus(order.getUser().getEmail(), order.getUser().getName(), completed, total);
            order.setLastStatusEmailAt(now);
            orderRepo.save(order);
        }

        if (daysSincePaid >= FINAL_DAY && order.getDay8EmailSentAt() == null) {
            emailService.sendOrgDay8Final(order.getUser().getEmail(), order.getUser().getName(), completed, total);
            order.setDay8EmailSentAt(now);
            orderRepo.save(order);
        }
    }

    private boolean isDue(LocalDateTime last, LocalDateTime now) {
        return last == null || Duration.between(last, now).compareTo(ORG_EMAIL_CADENCE) >= 0;
    }

    private boolean hasStartedAnyPulse(OrderEmployee emp, Order order) {
        if (emp.getUser() == null) return false;
        return PULSE_SEQUENCE.stream().anyMatch(p ->
                !sessionRepo.findByUserAndOrderAndPulse(emp.getUser(), order, p).isEmpty());
    }

    private boolean hasCompletedAllPulses(OrderEmployee emp, Order order) {
        if (emp.getUser() == null) return false;
        return PULSE_SEQUENCE.stream().allMatch(p ->
                sessionRepo.findByUserAndOrderAndPulse(emp.getUser(), order, p).stream()
                        .anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED));
    }

    private String displayName(String pulseCode) {
        return switch (pulseCode) {
            case "LEADER_PULSE" -> "LeaderPulse";
            case "TALENT_PULSE" -> "TalentPulse";
            case "SYSTEM_PULSE" -> "SystemPulse";
            case "GROWTH_PULSE" -> "GrowthPulse";
            default -> pulseCode;
        };
    }
}
