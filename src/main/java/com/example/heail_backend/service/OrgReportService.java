package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgReportService {

    private static final List<String> PULSE_SEQUENCE =
            List.of("LEADER_PULSE", "TALENT_PULSE", "SYSTEM_PULSE", "GROWTH_PULSE");

    private static final Map<String, List<String>> PULSE_SECTIONS = Map.of(
            "LEADER_PULSE", List.of("S01", "S02", "S03", "S11", "S16"),
            "TALENT_PULSE", List.of("S04", "S05", "S06", "S07", "S08"),
            "SYSTEM_PULSE", List.of("S09", "S10", "S12", "S13", "S14"),
            "GROWTH_PULSE", List.of("S15", "S17", "S18", "S19", "S20")
    );

    private static final int MIN_RESPONDENTS = 5;
    private static final int PARTIAL_THRESHOLD_DAY = 8;
    private static final double PARTIAL_THRESHOLD_PCT = 0.80;

    private final OrderEmployeeRepository orderEmployeeRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final SectionScoreRepository sectionScoreRepo;
    private final OrgSectionResultRepository orgSectionResultRepo;
    private final OrderRepository orderRepo;
    private final SectionRepository sectionRepo;
    private final EmailService emailService;

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

    private void computeAndRelease(Order order, List<OrderEmployee> fullyCompleted) {
        orgSectionResultRepo.deleteByOrder(order);

        List<AssessmentSession> completedSessions = new ArrayList<>();
        for (OrderEmployee emp : fullyCompleted) {
            for (String pulse : PULSE_SEQUENCE) {
                completedSessions.addAll(sessionRepo.findByUserAndOrderAndPulse(emp.getUser(), order, pulse).stream()
                        .filter(s -> s.getStatus() == SessionStatus.COMPLETED).toList());
            }
        }

        Map<String, List<BigDecimal>> bySection = sectionScoreRepo.findBySessionIn(completedSessions).stream()
                .collect(Collectors.groupingBy(SectionScore::getSectionCode,
                        Collectors.mapping(SectionScore::getEmployeeAvg, Collectors.toList())));

        for (Map.Entry<String, List<BigDecimal>> e : bySection.entrySet()) {
            List<BigDecimal> scores = e.getValue();
            OrgSectionResult result = new OrgSectionResult();
            result.setOrder(order);
            result.setSectionCode(e.getKey());
            result.setRespondentCount(scores.size());
            if (scores.size() >= MIN_RESPONDENTS) {
                BigDecimal avg = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
                result.setAvgScore(avg);
                result.setRag(ragFor(avg));
            } else {
                result.setAvgScore(null);
                result.setRag("INSUFFICIENT_DATA");
            }
            orgSectionResultRepo.save(result);
        }

        order.setReportReleasedAt(LocalDateTime.now());
        orderRepo.save(order);

        log.info("Report released for order {} ({} of {} employees fully completed)",
                order.getId(), fullyCompleted.size(), orderEmployeeRepo.findByOrder(order).size());
        emailService.sendOrgReportReleased(order.getUser().getEmail(), order.getUser().getName());
    }

    private String ragFor(BigDecimal avg) {
        double v = avg.doubleValue();
        if (v < 2.50) return "RED";
        if (v < 3.50) return "AMBER";
        return "GREEN";
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

    /* ── Aggregate RAG report: overall, per-Pulse, per-section — never individual scores ── */
    @Transactional(readOnly = true)
    public OrgReportResponse buildReport(Order order) {
        List<OrgSectionResult> results = orgSectionResultRepo.findByOrder(order);

        Map<String, String> sectionNames = sectionRepo.findAll().stream()
                .collect(Collectors.toMap(Section::getCode, Section::getName));

        List<SectionRagDto> sectionDtos = results.stream().map(r -> {
            SectionRagDto dto = new SectionRagDto();
            dto.setSectionCode(r.getSectionCode());
            dto.setSectionName(sectionNames.getOrDefault(r.getSectionCode(), r.getSectionCode()));
            dto.setRag(r.getRag());
            dto.setAvgScore(r.getAvgScore());
            dto.setRespondentCount(r.getRespondentCount());
            return dto;
        }).sorted(Comparator.comparingInt(d -> ragOrder(d.getRag()))).toList();

        List<PulseRagDto> pulseDtos = new ArrayList<>();
        for (String pulse : PULSE_SEQUENCE) {
            List<String> sectionCodes = PULSE_SECTIONS.get(pulse);
            List<BigDecimal> scores = results.stream()
                    .filter(r -> sectionCodes.contains(r.getSectionCode()) && r.getAvgScore() != null)
                    .map(OrgSectionResult::getAvgScore).toList();

            PulseRagDto dto = new PulseRagDto();
            dto.setPulseCode(pulse);
            dto.setDisplayName(displayName(pulse));
            if (scores.isEmpty()) {
                dto.setRag("INSUFFICIENT_DATA");
            } else {
                BigDecimal avg = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
                dto.setAvgScore(avg);
                dto.setRag(ragFor(avg));
            }
            pulseDtos.add(dto);
        }

        List<BigDecimal> allScores = results.stream().map(OrgSectionResult::getAvgScore).filter(Objects::nonNull).toList();
        String overallRag;
        if (allScores.isEmpty()) {
            overallRag = "INSUFFICIENT_DATA";
        } else {
            BigDecimal overallAvg = allScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(allScores.size()), 2, RoundingMode.HALF_UP);
            overallRag = ragFor(overallAvg);
        }

        int respondentCount = results.stream().mapToInt(OrgSectionResult::getRespondentCount).max().orElse(0);

        OrgReportResponse res = new OrgReportResponse();
        res.setOrderId(order.getId());
        res.setReleasedAt(order.getReportReleasedAt());
        res.setRespondentCount(respondentCount);
        res.setTotalEmployees(orderEmployeeRepo.findByOrder(order).size());
        res.setOverallRag(overallRag);
        res.setPulses(pulseDtos);
        res.setSections(sectionDtos);
        return res;
    }

    private int ragOrder(String rag) {
        return switch (rag) {
            case "RED" -> 0;
            case "AMBER" -> 1;
            case "GREEN" -> 2;
            default -> 3;
        };
    }

    private String displayName(String pulseCode) {
        return switch (pulseCode) {
            case "LEADER_PULSE" -> "LeaderPulse™";
            case "TALENT_PULSE" -> "TalentPulse™";
            case "SYSTEM_PULSE" -> "SystemPulse™";
            case "GROWTH_PULSE" -> "GrowthPulse™";
            default -> pulseCode;
        };
    }
}
