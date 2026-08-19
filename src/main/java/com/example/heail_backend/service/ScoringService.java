package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the index-architecture report (see heail-index-architecture.md)
 * from raw response rows — never averages an average where the raw rows are
 * available (Family 1), and never computes a gap/divergence/consensus figure
 * from anything but the fixed anchor questions (Family 2), since only those
 * are guaranteed to be the same question answered by every level.
 *
 * Suppression floor: RESPONDENT_FLOOR is deliberately 1, not the spec's
 * recommended 5 per level — an explicit call to show real numbers on small
 * pilot rounds (starting at 5 total employees across 3 levels) rather than
 * "not enough data" everywhere. This trades away statistical confidence and
 * some anonymity margin at very small n; raise it back toward 5 once rounds
 * are big enough that the tradeoff isn't necessary. Whatever the floor is,
 * below it the figure is a null with a stated reason, never a zero.
 * The spec's "10 shared questions" floor is NOT applied at section
 * granularity — with only 1-2 anchors curated per section (see
 * AnchorSelectionRunner), a literal per-section 10-question floor would
 * suppress nearly every section gap, including in the reference mockup this
 * was built against. Instead every section gap carries its actual
 * sharedQuestionCount so the reader can judge confidence directly, and the
 * 10-question floor is honoured at Pulse scope instead, where ~10 anchors
 * (5 sections x ~2) is what's actually available.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private static final int RESPONDENT_FLOOR = 1;
    private static final int STRAIGHTLINE_RUN = 8;

    private static final List<String> PULSE_SEQUENCE =
            List.of("LEADER_PULSE", "TALENT_PULSE", "SYSTEM_PULSE", "GROWTH_PULSE");
    private static final Map<String, List<String>> PULSE_SECTIONS = Map.of(
            "LEADER_PULSE", List.of("S01", "S02", "S03", "S11", "S16"),
            "TALENT_PULSE", List.of("S04", "S05", "S06", "S07", "S08"),
            "SYSTEM_PULSE", List.of("S09", "S10", "S12", "S13", "S14"),
            "GROWTH_PULSE", List.of("S15", "S17", "S18", "S19", "S20"));
    private static final Map<String, String> PULSE_DISPLAY_NAMES = Map.of(
            "LEADER_PULSE", "LeaderPulse", "TALENT_PULSE", "TalentPulse",
            "SYSTEM_PULSE", "SystemPulse", "GROWTH_PULSE", "GrowthPulse");

    // Family 4 (Risk) section mapping — fixed by definition, see index architecture doc.
    private static final String TRUST_SECTION = "S02";       // Management Integrity & Trust
    private static final String ATTRITION_SECTION = "S08";   // Attrition, Exit Management & Offboarding
    private static final String CHANGE_FATIGUE_SECTIONS_A = "S20"; // Overall Transformation Readiness
    private static final String CHANGE_FATIGUE_SECTIONS_B = "S16"; // Culture, Fears & Deep Blockers

    private final AssessmentSessionRepository sessionRepo;
    private final AnswerRepository answerRepo;
    private final QuestionBankRepository questionBankRepo;
    private final SectionRepository sectionRepo;

    private record AnswerRow(UUID userId, String level, String pulseCode, String sectionCode,
                              boolean isAnchor, String questionId, String questionText, short score,
                              char selectedOption) {}

    private record GapResult(Double l, Double mm, Double e, Double gap, int nL, int nMM, int nE,
                              String suppressedReason) {}

    public OrgReportResponse computeReport(Order order, List<OrderEmployee> completedEmployees) {
        Map<UUID, String> levelByUserId = completedEmployees.stream()
                .collect(Collectors.toMap(emp -> emp.getUser().getId(), OrderEmployee::getLevel, (a, b) -> a));

        List<AssessmentSession> sessions = sessionRepo.findByOrderAndStatus(order, SessionStatus.COMPLETED).stream()
                .filter(s -> levelByUserId.containsKey(s.getUser().getId()))
                .toList();

        List<UUID> sessionIds = sessions.stream().map(AssessmentSession::getId).toList();
        List<Answer> answers = sessionIds.isEmpty() ? List.of() : answerRepo.findBySessionIdIn(sessionIds);

        List<String> questionIds = answers.stream().map(Answer::getQuestionId).distinct().toList();
        Map<String, QuestionBank> questionsById = questionBankRepo.findByQuestionIdIn(questionIds).stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, q -> q));

        Map<UUID, AssessmentSession> sessionsById = sessions.stream()
                .collect(Collectors.toMap(AssessmentSession::getId, s -> s));

        List<AnswerRow> rows = new ArrayList<>(answers.size());
        for (Answer a : answers) {
            AssessmentSession session = sessionsById.get(a.getSession().getId());
            QuestionBank q = questionsById.get(a.getQuestionId());
            if (session == null || q == null) continue;
            String level = levelByUserId.get(session.getUser().getId());
            if (level == null) continue;
            rows.add(new AnswerRow(session.getUser().getId(), level, session.getPulse(), q.getSectionCode(),
                    q.isAnchor(), q.getQuestionId(), q.getText(), a.getScore(), a.getSelectedOption()));
        }

        Map<String, String> sectionNames = sectionRepo.findAll().stream()
                .collect(Collectors.toMap(Section::getCode, Section::getName));

        List<SectionRagDto> sections = buildSections(rows, sectionNames);
        List<PulseRagDto> pulses = buildPulses(rows, sections);
        List<QuestionFindingDto> findings = buildFindings(rows, sectionNames);
        DataQualityDto dataQuality = buildDataQuality(rows, sessions, completedEmployees.size());
        RiskIndicesDto risk = buildRisk(rows, pulses);

        double overallIndex = index(rows);
        Double overallDivergence = meanAbsGap(sections);

        OrgReportResponse res = new OrgReportResponse();
        res.setOrderId(order.getId());
        res.setOrganisationName(order.getUser().getOrganisation() != null
                ? order.getUser().getOrganisation().getName() : null);
        res.setReleasedAt(order.getReportReleasedAt());
        res.setRespondentCount(completedEmployees.size());
        res.setTotalEmployees(completedEmployees.size());
        res.setRespondentsL(countLevel(completedEmployees, "L"));
        res.setRespondentsMM(countLevel(completedEmployees, "MM"));
        res.setRespondentsE(countLevel(completedEmployees, "E"));
        res.setOverallIndex(round1(overallIndex));
        res.setOverallBand(band(overallIndex));
        res.setOverallRag(ragFor(overallIndex));
        res.setDivergenceIndex(overallDivergence);
        res.setDataQuality(dataQuality);
        res.setRisk(risk);
        res.setPulses(pulses);
        res.setSections(sections);
        res.setFindings(findings);
        return res;
    }

    /* ── Sections ───────────────────────────────────────────────── */
    private List<SectionRagDto> buildSections(List<AnswerRow> rows, Map<String, String> sectionNames) {
        Map<String, List<AnswerRow>> bySection = rows.stream().collect(Collectors.groupingBy(AnswerRow::sectionCode));
        List<SectionRagDto> sections = new ArrayList<>();

        for (Map.Entry<String, List<AnswerRow>> e : bySection.entrySet()) {
            String sectionCode = e.getKey();
            List<AnswerRow> sectionRows = e.getValue();
            List<AnswerRow> anchorRows = sectionRows.stream().filter(AnswerRow::isAnchor).toList();

            double idx = index(sectionRows);
            GapResult gapResult = computeGap(anchorRows);

            SectionRagDto dto = new SectionRagDto();
            dto.setSectionCode(sectionCode);
            dto.setSectionName(sectionNames.getOrDefault(sectionCode, sectionCode));
            dto.setPulseCode(pulseForSection(sectionCode));
            dto.setIndex(round1(idx));
            dto.setBand(band(idx));
            dto.setRag(ragFor(idx));
            dto.setLevelL(roundOrNull(gapResult.l()));
            dto.setLevelMM(roundOrNull(gapResult.mm()));
            dto.setLevelE(roundOrNull(gapResult.e()));
            dto.setGap(roundOrNull(gapResult.gap()));
            dto.setSuppressedReason(gapResult.suppressedReason());
            dto.setSharedQuestionCount((int) anchorRows.stream().map(AnswerRow::questionId).distinct().count());
            dto.setRespondentCount((int) sectionRows.stream().map(AnswerRow::userId).distinct().count());
            sections.add(dto);
        }

        sections.sort(Comparator.comparing((SectionRagDto s) -> s.getGap() == null)
                .thenComparing((SectionRagDto s) -> s.getGap() == null ? 0 : -Math.abs(s.getGap())));
        return sections;
    }

    /* ── Pulses ─────────────────────────────────────────────────── */
    private List<PulseRagDto> buildPulses(List<AnswerRow> rows, List<SectionRagDto> sections) {
        List<PulseRagDto> pulses = new ArrayList<>();

        for (String pulseCode : PULSE_SEQUENCE) {
            List<AnswerRow> pulseRows = rows.stream().filter(r -> pulseCode.equals(r.pulseCode())).toList();
            List<AnswerRow> anchorRows = pulseRows.stream().filter(AnswerRow::isAnchor).toList();

            double idx = index(pulseRows);
            GapResult gapResult = computeGap(anchorRows);

            List<Double> sectionGaps = sections.stream()
                    .filter(s -> pulseCode.equals(s.getPulseCode()) && s.getGap() != null)
                    .map(s -> Math.abs(s.getGap()))
                    .toList();
            Double divergence = sectionGaps.isEmpty() ? null
                    : round1(sectionGaps.stream().mapToDouble(Double::doubleValue).average().orElse(0));

            Double alignment = null;
            if (gapResult.gap() != null && gapResult.mm() != null && Math.abs(gapResult.gap()) >= 5) {
                alignment = round2((gapResult.mm() - gapResult.e()) / (gapResult.l() - gapResult.e()));
            }

            Double consensus = consensus(anchorRows);
            Double polarisation = polarisation(anchorRows);

            PulseRagDto dto = new PulseRagDto();
            dto.setPulseCode(pulseCode);
            dto.setDisplayName(PULSE_DISPLAY_NAMES.get(pulseCode));
            dto.setIndex(round1(idx));
            dto.setBand(band(idx));
            dto.setRag(ragFor(idx));
            dto.setNetGap(roundOrNull(gapResult.gap()));
            dto.setDivergenceIndex(divergence);
            dto.setManagementAlignment(alignment);
            dto.setConsensus(consensus);
            dto.setPolarisation(polarisation);
            pulses.add(dto);
        }

        // Flag the pulse with the largest absolute divergence, if it's meaningfully ahead of the rest.
        pulses.stream()
                .filter(p -> p.getDivergenceIndex() != null)
                .max(Comparator.comparing(PulseRagDto::getDivergenceIndex))
                .ifPresent(top -> {
                    boolean clearLead = pulses.stream()
                            .filter(p -> p != top && p.getDivergenceIndex() != null)
                            .allMatch(p -> top.getDivergenceIndex() - p.getDivergenceIndex() >= 5);
                    if (clearLead) top.setWarning("Highest divergence of the four Pulses — leadership and "
                            + "delivery staff are furthest apart on the sections this Pulse covers.");
                });

        return pulses;
    }

    /* ── Question-level findings ───────────────────────────────────── */
    private List<QuestionFindingDto> buildFindings(List<AnswerRow> rows, Map<String, String> sectionNames) {
        Map<String, List<AnswerRow>> byQuestion = rows.stream()
                .filter(AnswerRow::isAnchor)
                .collect(Collectors.groupingBy(AnswerRow::questionId));

        List<QuestionFindingDto> findings = new ArrayList<>();
        for (Map.Entry<String, List<AnswerRow>> e : byQuestion.entrySet()) {
            List<AnswerRow> questionRows = e.getValue();
            GapResult g = computeGap(questionRows);
            if (g.gap() == null) continue;

            AnswerRow sample = questionRows.get(0);
            QuestionFindingDto dto = new QuestionFindingDto();
            dto.setQuestionId(e.getKey());
            dto.setSectionCode(sample.sectionCode());
            dto.setSectionName(sectionNames.getOrDefault(sample.sectionCode(), sample.sectionCode()));
            dto.setPulseCode(sample.pulseCode());
            dto.setText(sample.questionText());
            dto.setLevelL(roundOrNull(g.l()));
            dto.setLevelMM(roundOrNull(g.mm()));
            dto.setLevelE(roundOrNull(g.e()));
            dto.setGap(round1(g.gap()));
            dto.setNL(g.nL());
            dto.setNMM(g.nMM());
            dto.setNE(g.nE());
            findings.add(dto);
        }

        return findings.stream()
                .sorted(Comparator.comparing((QuestionFindingDto f) -> Math.abs(f.getGap())).reversed())
                .limit(5)
                .toList();
    }

    /* ── Data quality ───────────────────────────────────────────── */
    private DataQualityDto buildDataQuality(List<AnswerRow> rows, List<AssessmentSession> sessions,
                                             int totalRespondents) {
        long naCount = rows.stream().filter(r -> r.selectedOption() == 'N').count();
        double silenceRatePct = rows.isEmpty() ? 0 : round1(100.0 * naCount / rows.size());

        Set<UUID> straightliners = detectStraightliners(sessions);

        double straightlineRespondentRate = totalRespondents == 0 ? 0
                : (double) straightliners.size() / totalRespondents;
        double voiceConfidence = clamp01(1 - (silenceRatePct / 100.0) - straightlineRespondentRate);

        long suppressedSections = 0; // filled in by caller via sections list in computeReport if needed

        DataQualityDto dto = new DataQualityDto();
        dto.setRespondentsL(countByLevel(rows, "L"));
        dto.setRespondentsMM(countByLevel(rows, "MM"));
        dto.setRespondentsE(countByLevel(rows, "E"));
        dto.setSharedQuestions((int) rows.stream().filter(AnswerRow::isAnchor).map(AnswerRow::questionId).distinct().count());
        dto.setStraightliningCount(straightliners.size());
        dto.setSilenceRatePct(silenceRatePct);
        dto.setVoiceConfidence(round2(voiceConfidence));
        dto.setSuppressedSectionCount((int) suppressedSections);
        dto.setMedianCompletionMinutes(medianCompletionMinutes(sessions));
        return dto;
    }

    /* ── Risk indices (Family 4) ───────────────────────────────────── */
    private RiskIndicesDto buildRisk(List<AnswerRow> rows, List<PulseRagDto> pulses) {
        RiskIndicesDto dto = new RiskIndicesDto();
        dto.setTrustDeficit(roundOrNull(indexForSectionLevel(rows, TRUST_SECTION, "E")));
        dto.setAttritionSignal(roundOrNull(indexForSectionLevel(rows, ATTRITION_SECTION, "E")));

        Double changeA = indexForSectionLevel(rows, CHANGE_FATIGUE_SECTIONS_A, "E");
        Double changeB = indexForSectionLevel(rows, CHANGE_FATIGUE_SECTIONS_B, "E");
        if (changeA != null && changeB != null) dto.setChangeFatigue(round1((changeA + changeB) / 2));

        Double growth = pulseIndexOf(pulses, "GROWTH_PULSE");
        Double system = pulseIndexOf(pulses, "SYSTEM_PULSE");
        if (growth != null && system != null) dto.setExecutionDrag(round1(growth - system));

        return dto;
    }

    private Double indexForSectionLevel(List<AnswerRow> rows, String sectionCode, String level) {
        List<AnswerRow> scoped = rows.stream()
                .filter(r -> sectionCode.equals(r.sectionCode()) && level.equals(r.level())).toList();
        if (scoped.isEmpty()) return null;
        return index(scoped);
    }

    private Double pulseIndexOf(List<PulseRagDto> pulses, String pulseCode) {
        return pulses.stream().filter(p -> pulseCode.equals(p.getPulseCode())).findFirst()
                .map(PulseRagDto::getIndex).orElse(null);
    }

    /* ── Straight-lining ────────────────────────────────────────────
       8+ consecutive identical answers within one session (a section only
       has 6 questions, so this can only be observed across a whole Pulse's
       30 — the spec's "within a section" wording assumes a denser per-
       section draw than this instrument currently uses). Ordered by
       answered_at, which is the closest proxy available to display order. */
    private Set<UUID> detectStraightliners(List<AssessmentSession> sessions) {
        Set<UUID> flagged = new HashSet<>();
        for (AssessmentSession session : sessions) {
            List<Answer> answers = answerRepo.findBySessionId(session.getId()).stream()
                    .sorted(Comparator.comparing(Answer::getAnsweredAt))
                    .toList();
            int run = 1;
            for (int i = 1; i < answers.size(); i++) {
                if (answers.get(i).getSelectedOption() == answers.get(i - 1).getSelectedOption()) {
                    run++;
                    if (run >= STRAIGHTLINE_RUN) { flagged.add(session.getUser().getId()); break; }
                } else {
                    run = 1;
                }
            }
        }
        return flagged;
    }

    private Double medianCompletionMinutes(List<AssessmentSession> sessions) {
        List<Double> minutes = sessions.stream()
                .filter(s -> s.getStartedAt() != null && s.getCompletedAt() != null)
                .map(s -> Duration.between(s.getStartedAt(), s.getCompletedAt()).toSeconds() / 60.0)
                .sorted()
                .toList();
        if (minutes.isEmpty()) return null;
        int mid = minutes.size() / 2;
        double median = minutes.size() % 2 == 0 ? (minutes.get(mid - 1) + minutes.get(mid)) / 2 : minutes.get(mid);
        return round1(median);
    }

    /* ── Core statistics ───────────────────────────────────────────── */
    private double index(List<AnswerRow> rows) {
        if (rows.isEmpty()) return 0;
        double mean = rows.stream().mapToInt(AnswerRow::score).average().orElse(0);
        return clamp((mean - 1) / 4 * 100, 0, 100);
    }

    private GapResult computeGap(List<AnswerRow> anchorRows) {
        Map<String, List<AnswerRow>> byLevel = anchorRows.stream().collect(Collectors.groupingBy(AnswerRow::level));
        List<AnswerRow> lRows = byLevel.getOrDefault("L", List.of());
        List<AnswerRow> mmRows = byLevel.getOrDefault("MM", List.of());
        List<AnswerRow> eRows = byLevel.getOrDefault("E", List.of());

        int nL = distinctRespondents(lRows), nMM = distinctRespondents(mmRows), nE = distinctRespondents(eRows);

        if (nL < RESPONDENT_FLOOR || nE < RESPONDENT_FLOOR) {
            return new GapResult(
                    lRows.isEmpty() ? null : index(lRows),
                    mmRows.isEmpty() ? null : index(mmRows),
                    eRows.isEmpty() ? null : index(eRows),
                    null, nL, nMM, nE,
                    "needs " + RESPONDENT_FLOOR + "+ respondents at both L and E levels (currently L="
                            + nL + ", E=" + nE + ")");
        }

        double l = index(lRows), e = index(eRows);
        Double mm = mmRows.isEmpty() ? null : index(mmRows);
        return new GapResult(l, mm, e, round1(l - e), nL, nMM, nE, null);
    }

    private Double consensus(List<AnswerRow> rows) {
        if (rows.size() < 2) return null;
        double mean = rows.stream().mapToInt(AnswerRow::score).average().orElse(0);
        double variance = rows.stream().mapToDouble(r -> Math.pow(r.score() - mean, 2)).average().orElse(0);
        double sd = Math.sqrt(variance);
        // Max plausible SD on a bounded 1-5 scale (an even 1/5 split) is 2 — used only to
        // rescale SD onto a 0-100 "how much disagreement" axis, not a formal normalisation.
        double normalisedSd = clamp(sd / 2.0 * 100, 0, 100);
        return round1(100 - normalisedSd);
    }

    /** Sarle's bimodality coefficient: (skewness^2 + 1) / kurtosis. Distinguishes "spread out"
     *  from "split into two camps" — the latter needs a different fix than the former. */
    private Double polarisation(List<AnswerRow> rows) {
        if (rows.size() < 8) return null;
        double n = rows.size();
        double mean = rows.stream().mapToInt(AnswerRow::score).average().orElse(0);
        double m2 = rows.stream().mapToDouble(r -> Math.pow(r.score() - mean, 2)).sum() / n;
        double m3 = rows.stream().mapToDouble(r -> Math.pow(r.score() - mean, 3)).sum() / n;
        double m4 = rows.stream().mapToDouble(r -> Math.pow(r.score() - mean, 4)).sum() / n;
        if (m2 == 0) return null;
        double skew = m3 / Math.pow(m2, 1.5);
        double kurtosis = m4 / Math.pow(m2, 2);
        if (kurtosis == 0) return null;
        return round2((skew * skew + 1) / kurtosis);
    }

    private Double meanAbsGap(List<SectionRagDto> sections) {
        List<Double> gaps = sections.stream().map(SectionRagDto::getGap).filter(Objects::nonNull).toList();
        if (gaps.isEmpty()) return null;
        return round1(gaps.stream().mapToDouble(Math::abs).average().orElse(0));
    }

    /* ── Small helpers ──────────────────────────────────────────── */
    private int distinctRespondents(List<AnswerRow> rows) {
        return (int) rows.stream().map(AnswerRow::userId).distinct().count();
    }

    private int countByLevel(List<AnswerRow> rows, String level) {
        return (int) rows.stream().filter(r -> level.equals(r.level())).map(AnswerRow::userId).distinct().count();
    }

    private int countLevel(List<OrderEmployee> employees, String level) {
        return (int) employees.stream().filter(e -> level.equals(e.getLevel())).count();
    }

    private String pulseForSection(String sectionCode) {
        for (var e : PULSE_SECTIONS.entrySet()) if (e.getValue().contains(sectionCode)) return e.getKey();
        return null;
    }

    private String band(double index) {
        if (index < 40) return "emerging";
        if (index < 60) return "developing";
        if (index < 80) return "established";
        return "leading";
    }

    private String ragFor(double index) {
        if (index < 37.5) return "RED";
        if (index < 60) return "AMBER";
        return "GREEN";
    }

    private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private double clamp01(double v) { return clamp(v, 0, 1); }
    private double round1(double v) { return Math.round(v * 10) / 10.0; }
    private double round2(double v) { return Math.round(v * 100) / 100.0; }
    private Double roundOrNull(Double v) { return v == null ? null : round1(v); }
}
