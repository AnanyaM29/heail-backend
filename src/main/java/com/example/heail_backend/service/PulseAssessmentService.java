package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PulseAssessmentService {

    private static final String SUITE_4PULSE_PRODUCT = "SUITE_4PULSE";

    private static final List<String> PULSE_SEQUENCE =
            List.of("LEADER_PULSE", "TALENT_PULSE", "SYSTEM_PULSE", "GROWTH_PULSE");

    private static final Map<String, List<String>> PULSE_SECTIONS = Map.of(
            "LEADER_PULSE", List.of("S01", "S02", "S03", "S11", "S16"),
            "TALENT_PULSE", List.of("S04", "S05", "S06", "S07", "S08"),
            "SYSTEM_PULSE", List.of("S09", "S10", "S12", "S13", "S14"),
            "GROWTH_PULSE", List.of("S15", "S17", "S18", "S19", "S20")
    );

    private static final List<String> CATEGORY_CODES =
            IntStream.rangeClosed(1, 10).mapToObj(i -> String.format("C%02d", i)).toList();

    private static final int QUESTIONS_PER_SECTION = 6;

    private final OrderEmployeeRepository orderEmployeeRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final AnswerRepository answerRepo;
    private final QuestionBankRepository questionBankRepo;
    private final UserRepository userRepo;
    private final SectionScoreRepository sectionScoreRepo;
    private final OrgReportService orgReportService;

    private final SecureRandom secureRandom = new SecureRandom();

    /* ── All 4 Pulses' state for the calling employee ─────────────── */
    @Transactional(readOnly = true)
    public PulseStatusResponse status(String email) {
        User user = requireUser(email);
        OrderEmployee membership = requireActiveRound(user);
        Order order = membership.getOrder();

        List<PulseInfo> pulses = new ArrayList<>();
        boolean previousCompleted = true; // the first Pulse in sequence is always unlockable

        for (String pulseCode : PULSE_SEQUENCE) {
            List<AssessmentSession> sessions = sessionRepo.findByUserAndOrderAndPulse(user, order, pulseCode);
            Optional<AssessmentSession> completed = sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.COMPLETED).findFirst();
            Optional<AssessmentSession> inProgress = sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS).findFirst();

            PulseInfo info = new PulseInfo();
            info.setPulseCode(pulseCode);

            if (completed.isPresent()) {
                info.setState("COMPLETED");
                info.setSessionId(completed.get().getId());
                info.setCompletedAt(completed.get().getCompletedAt());
            } else if (inProgress.isPresent()) {
                info.setState("IN_PROGRESS");
                info.setSessionId(inProgress.get().getId());
            } else if (previousCompleted) {
                info.setState("PENDING");
            } else {
                info.setState("LOCKED");
            }

            pulses.add(info);
            previousCompleted = completed.isPresent();
        }

        PulseStatusResponse res = new PulseStatusResponse();
        res.setOrderId(order.getId());
        res.setPulses(pulses);
        res.setAllCompleted(pulses.stream().allMatch(p -> "COMPLETED".equals(p.getState())));
        return res;
    }

    /* ── Start (or resume, if already in progress) a Pulse ────────── */
    @Transactional
    public StartAssessmentResponse start(String pulseCode, String email) {
        if (!PULSE_SEQUENCE.contains(pulseCode))
            throw new IllegalArgumentException("Unknown pulse: " + pulseCode);

        User user = requireUser(email);
        OrderEmployee membership = requireActiveRound(user);
        Order order = membership.getOrder();
        String level = membership.getLevel();

        int pulseIndex = PULSE_SEQUENCE.indexOf(pulseCode);
        for (int i = 0; i < pulseIndex; i++) {
            String priorPulse = PULSE_SEQUENCE.get(i);
            boolean priorCompleted = sessionRepo.findByUserAndOrderAndPulse(user, order, priorPulse).stream()
                    .anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED);
            if (!priorCompleted)
                throw new AccessDeniedException(
                        "Complete " + displayName(priorPulse) + " before starting " + displayName(pulseCode));
        }

        List<AssessmentSession> existing = sessionRepo.findByUserAndOrderAndPulse(user, order, pulseCode);
        if (existing.stream().anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED))
            throw new AccessDeniedException(displayName(pulseCode) + " has already been completed for this round");

        Optional<AssessmentSession> inProgress = existing.stream()
                .filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS).findFirst();
        if (inProgress.isPresent()) return toStartResponse(inProgress.get());

        List<String> questionIds = generateQuestionIds(pulseCode, level);

        AssessmentSession session = new AssessmentSession();
        session.setUser(user);
        session.setProductCode(SUITE_4PULSE_PRODUCT);
        session.setPulse(pulseCode);
        session.setOrder(order);
        session.setAttemptNumber(1);
        session.setQuestionIds(questionIds);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session = sessionRepo.save(session);

        return toStartResponse(session);
    }

    /* ── Resume: current questions + what's already answered ─────── */
    @Transactional(readOnly = true)
    public SessionResumeResponse resume(UUID sessionId, String email) {
        AssessmentSession session = requireOwnedSession(sessionId, email);

        Map<String, String> answered = answerRepo.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> String.valueOf(a.getSelectedOption())));

        SessionResumeResponse res = new SessionResumeResponse();
        res.setSessionId(session.getId());
        res.setAttemptNumber(session.getAttemptNumber());
        res.setStatus(session.getStatus().name());
        res.setQuestions(toOrderedQuestionDtos(session.getQuestionIds()));
        res.setAnsweredOptions(answered);
        return res;
    }

    /* ── Autosave one answer (upsert) ─────────────────────────────── */
    @Transactional
    public AnswerResponse answer(UUID sessionId, String email, AnswerRequest req) {
        AssessmentSession session = requireOwnedSession(sessionId, email);
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw new IllegalStateException("This Pulse has already been submitted");
        if (!session.getQuestionIds().contains(req.getQuestionId()))
            throw new IllegalArgumentException("Question is not part of this session");

        QuestionBank question = questionBankRepo.findById(req.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown question: " + req.getQuestionId()));

        char selected = req.getSelectedOption().charAt(0);
        short score = question.scoreFor(selected);

        Answer answer = answerRepo.findBySessionIdAndQuestionId(sessionId, req.getQuestionId())
                .orElseGet(Answer::new);
        answer.setSession(session);
        answer.setQuestionId(req.getQuestionId());
        answer.setSelectedOption(selected);
        answer.setScore(score);
        answerRepo.save(answer);

        int answeredCount = answerRepo.findBySessionId(sessionId).size();

        AnswerResponse res = new AnswerResponse();
        res.setQuestionId(req.getQuestionId());
        res.setSelectedOption(req.getSelectedOption());
        res.setAnsweredCount(answeredCount);
        res.setTotalQuestions(session.getQuestionIds().size());
        return res;
    }

    /* ── Submit: lock the session, score per-section, trigger the org report check ── */
    @Transactional
    public PulseSubmitResponse submit(UUID sessionId, String email) {
        AssessmentSession session = requireOwnedSession(sessionId, email);
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw new IllegalStateException("This Pulse has already been submitted");

        int total = session.getQuestionIds().size();
        List<Answer> answers = answerRepo.findBySessionId(sessionId);
        if (answers.size() < total)
            throw new IllegalArgumentException(
                    "Answer all " + total + " questions before submitting (" + answers.size() + " answered)");

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        AssessmentSession saved = sessionRepo.save(session);

        scoreSections(saved, answers);

        User sessionUser = saved.getUser();
        Order sessionOrder = saved.getOrder();
        boolean allCompleted = PULSE_SEQUENCE.stream().allMatch(p ->
                sessionRepo.findByUserAndOrderAndPulse(sessionUser, sessionOrder, p).stream()
                        .anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED));

        if (sessionOrder != null) orgReportService.checkAndMaybeCompute(sessionOrder);

        PulseSubmitResponse res = new PulseSubmitResponse();
        res.setSessionId(saved.getId());
        res.setPulseCode(saved.getPulse());
        res.setStatus(saved.getStatus().name());
        res.setAllPulsesCompleted(allCompleted);
        return res;
    }

    /* ── Per-section employee average, one row per section covered by this Pulse ── */
    private void scoreSections(AssessmentSession session, List<Answer> answers) {
        Map<String, QuestionBank> questionsById = questionBankRepo
                .findByQuestionIdIn(answers.stream().map(Answer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, q -> q));

        Map<String, List<Short>> bySection = new LinkedHashMap<>();
        for (Answer a : answers) {
            String sectionCode = questionsById.get(a.getQuestionId()).getSectionCode();
            bySection.computeIfAbsent(sectionCode, k -> new ArrayList<>()).add(a.getScore());
        }

        for (Map.Entry<String, List<Short>> e : bySection.entrySet()) {
            double avg = e.getValue().stream().mapToInt(Short::intValue).average().orElse(0);
            SectionScore sectionScore = new SectionScore();
            sectionScore.setSession(session);
            sectionScore.setSectionCode(e.getKey());
            sectionScore.setEmployeeAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            sectionScoreRepo.save(sectionScore);
        }
    }

    /* ── §6.2 generation algorithm ─────────────────────────────────
       6 questions per section (shuffle 10 categories, take first 6, one
       eligible question per category), 5 sections per Pulse = 30 total,
       shuffled together before being handed to the client. ────────── */
    private List<String> generateQuestionIds(String pulseCode, String level) {
        List<String> sections = PULSE_SECTIONS.get(pulseCode);
        List<String> questionIds = new ArrayList<>(sections.size() * QUESTIONS_PER_SECTION);

        for (String section : sections) {
            List<String> categories = new ArrayList<>(CATEGORY_CODES);
            shuffle(categories);
            List<String> chosenCategories = categories.subList(0, QUESTIONS_PER_SECTION);

            for (String category : chosenCategories) {
                List<QuestionBank> candidates =
                        questionBankRepo.findBySectionCodeAndCategoryCodeAndActiveTrue(section, category);

                List<QuestionBank> eligible = List.of();
                for (String tryLevel : fallbackChain(level)) {
                    List<QuestionBank> pool = candidates.stream()
                            .filter(q -> levelEligible(q.getLevelTag(), tryLevel)).toList();
                    if (!pool.isEmpty()) { eligible = pool; break; }
                }
                if (eligible.isEmpty())
                    throw new IllegalStateException("No eligible question for section " + section
                            + " category " + category + " level " + level + " (including fallback)");

                questionIds.add(eligible.get(secureRandom.nextInt(eligible.size())).getQuestionId());
            }
        }

        shuffle(questionIds);
        return questionIds;
    }

    private boolean levelEligible(String levelTag, String targetLevel) {
        return Arrays.asList(levelTag.split("\\+")).contains(targetLevel);
    }

    /** LEVEL_FALLBACK per §6.2: L→MM; MM→L,E; E→MM. The employee's own level is always tried first. */
    private List<String> fallbackChain(String level) {
        return switch (level) {
            case "L" -> List.of("L", "MM");
            case "MM" -> List.of("MM", "L", "E");
            case "E" -> List.of("E", "MM");
            default -> List.of(level);
        };
    }

    /* ── Private helpers ───────────────────────────────────────── */
    private void shuffle(List<String> ids) {
        for (int i = ids.size() - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            Collections.swap(ids, i, j);
        }
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

    private OrderEmployee requireActiveRound(User user) {
        // NOTE: an employee could in principle belong to more than one PAID round (re-added in a
        // later CSV, or enrolled by a second organisation under the same email). We just take the
        // most recently paid one for now — proper multi-round support (letting them choose, or
        // running Pulses per-round) is not handled in this pass.
        return orderEmployeeRepo.findByUser(user).stream()
                .filter(m -> m.getOrder().getStatus() == OrderStatus.PAID)
                .max(Comparator.comparing(m -> m.getOrder().getPaidAt()))
                .orElseThrow(() -> new AccessDeniedException("No paid diagnostic round found for this account"));
    }

    private StartAssessmentResponse toStartResponse(AssessmentSession session) {
        StartAssessmentResponse res = new StartAssessmentResponse();
        res.setSessionId(session.getId());
        res.setAttemptNumber(session.getAttemptNumber());
        res.setQuestions(toOrderedQuestionDtos(session.getQuestionIds()));
        return res;
    }

    private List<QuestionDto> toOrderedQuestionDtos(List<String> ids) {
        Map<String, QuestionBank> byId = questionBankRepo.findByQuestionIdIn(ids).stream()
                .collect(Collectors.toMap(QuestionBank::getQuestionId, q -> q));
        return ids.stream().map(id -> toQuestionDto(byId.get(id))).toList();
    }

    private QuestionDto toQuestionDto(QuestionBank q) {
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(q.getQuestionId());
        dto.setText(q.getText());
        dto.setOptionA(q.getOptionA());
        dto.setOptionB(q.getOptionB());
        dto.setOptionC(q.getOptionC());
        dto.setOptionD(q.getOptionD());
        return dto;
    }

    private AssessmentSession requireOwnedSession(UUID sessionId, String email) {
        AssessmentSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment session not found"));
        if (!session.getUser().getEmail().equalsIgnoreCase(email))
            throw new IllegalArgumentException("Assessment session not found");
        return session;
    }

    private User requireUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
