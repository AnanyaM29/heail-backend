package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import com.example.heail_backend.util.OptionOrder;
import com.example.heail_backend.util.SessionTimer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The HR Competency Assessment flow — 7 pillars, each a 30-question draw
 * stratified across that pillar's competencies. Modeled directly on
 * AssessmentService (entitlement → build questionIds → shuffle → score →
 * save result) and PulseAssessmentService's generateQuestionIds (stratified
 * sampling with a fill-the-remainder step), reusing the same generic
 * assessment_session/answers/entitlements tables Leader and Pulse already
 * share — see AssessmentSession/Answer/Entitlement for why those are
 * deliberately generic.
 *
 * One entitlement per pillar: product codes are "HR_A1".."HR_A7" (see
 * productCodeFor/assessmentIdFromProductCode). Individual self-serve only —
 * no org-bulk purchase flow in this pass.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HrAssessmentService {

    private static final String PRODUCT_PREFIX = "HR_A";
    private static final int HR_OPTION_COUNT = 5;
    /** The best-scoring option on every question is worth 5 (see HrQuestionBank) —
     *  used as the per-question denominator when converting a raw score sum into a
     *  percentage of what was achievable. */
    private static final int MAX_SCORE_PER_QUESTION = 5;

    private final EntitlementRepository entitlementRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final AnswerRepository answerRepo;
    private final HrAssessmentRepository hrAssessmentRepo;
    private final HrSkillCategoryRepository hrSkillCategoryRepo;
    private final HrCompetencyRepository hrCompetencyRepo;
    private final HrQuestionBankRepository hrQuestionBankRepo;
    private final HrResultRepository hrResultRepo;
    private final UserRepository userRepo;

    private final SecureRandom secureRandom = new SecureRandom();

    /* ── The 7 pillars, with whether the caller currently holds an unused
       entitlement for each — email is null for an anonymous browser (the
       pricing page shows this list with no login required), in which case
       every pillar is simply not-entitled. ───────────────────────────── */
    @Transactional(readOnly = true)
    public List<HrAssessmentDto> listAssessments(String email) {
        User user = email != null ? requireUser(email) : null;
        return hrAssessmentRepo.findAllByOrderByIdAsc().stream().map(a -> {
            HrAssessmentDto dto = new HrAssessmentDto();
            dto.setId(a.getId());
            dto.setCode(a.getCode());
            dto.setName(a.getName());
            dto.setQuestionCount(a.getQuestionCount());
            dto.setTimeMinutes(a.getTimeMinutes());
            dto.setEntitled(user != null && entitlementRepo
                    .findFirstByUserAndProductCodeAndUsedFalseOrderByCreatedAtAsc(user, productCodeFor(a.getId()))
                    .isPresent());
            // start() consumes the entitlement immediately, so a pillar the candidate has
            // already begun but not finished would otherwise flip to "not entitled, not
            // completed" and simply vanish from their list — surface it explicitly instead.
            if (user != null) {
                sessionRepo.findFirstByUserAndProductCodeAndStatusOrderByStartedAtDesc(
                                user, productCodeFor(a.getId()), SessionStatus.IN_PROGRESS)
                        .ifPresent(s -> dto.setInProgressSessionId(s.getId()));
                entitlementRepo.findFirstByUserAndProductCodeOrderByCreatedAtAsc(user, productCodeFor(a.getId()))
                        .ifPresent(e -> dto.setAssignedAt(e.getCreatedAt()));
            }
            return dto;
        }).toList();
    }

    /* ── Every individual assignment (entitlement) this person holds, across
       all 7 pillars — one card per assignment, even when the same pillar was
       assigned to them more than once (registered as a candidate on two
       separate orders). Entitlements and sessions aren't directly linked in
       the schema, but start() always consumes the OLDEST unused entitlement
       and assigns the next sequential attemptNumber, so pairing entitlement #i
       (oldest-first) with session #i (attemptNumber order) recovers exactly
       which assignment each session belongs to. ─────────────────────────── */
    @Transactional(readOnly = true)
    public List<HrAssignmentDto> listAssignments(String email) {
        User user = requireUser(email);

        List<Entitlement> allEntitlements = entitlementRepo.findByUser(user);
        List<HrResult> allResults = hrResultRepo.findByUserOrderByCreatedAtDesc(user);

        List<HrAssignmentDto> out = new ArrayList<>();
        for (HrAssessment a : hrAssessmentRepo.findAllByOrderByIdAsc()) {
            String productCode = productCodeFor(a.getId());

            List<Entitlement> entitlements = allEntitlements.stream()
                    .filter(e -> e.getProductCode().equals(productCode))
                    .sorted(Comparator.comparing(Entitlement::getCreatedAt))
                    .toList();
            if (entitlements.isEmpty()) continue; // never assigned this pillar at all

            List<AssessmentSession> sessions = sessionRepo.findByUserAndProductCodeOrderByAttemptNumberDesc(user, productCode)
                    .stream().sorted(Comparator.comparingInt(AssessmentSession::getAttemptNumber)).toList();

            // Sessions started via the entitlement-specific start() carry a direct
            // link — use it to pair a session to its exact assignment. A session
            // from before that link existed (entitlement null) falls back to
            // positional pairing among whatever's left, in creation order — the
            // only pairing possible under the old "always consume oldest unused"
            // start() rule, and still correct for that pre-existing data.
            Map<UUID, AssessmentSession> sessionByEntitlementId = new HashMap<>();
            Deque<AssessmentSession> legacySessions = new ArrayDeque<>();
            for (AssessmentSession s : sessions) {
                if (s.getEntitlement() != null) sessionByEntitlementId.put(s.getEntitlement().getId(), s);
                else legacySessions.addLast(s);
            }

            Map<Integer, HrResult> resultsByAttempt = allResults.stream()
                    .filter(r -> r.getAssessment().getId() == a.getId())
                    .collect(Collectors.toMap(HrResult::getAttemptNumber, r -> r, (x, y) -> x));

            for (int i = 0; i < entitlements.size(); i++) {
                Entitlement ent = entitlements.get(i);
                HrAssignmentDto dto = new HrAssignmentDto();
                dto.setEntitlementId(ent.getId());
                dto.setAssessmentId(a.getId());
                dto.setAssessmentCode(a.getCode());
                dto.setAssessmentName(a.getName());
                dto.setQuestionCount(a.getQuestionCount());
                dto.setTimeMinutes(a.getTimeMinutes());
                dto.setAssignedAt(ent.getCreatedAt());
                dto.setAttemptNumber(i + 1);

                AssessmentSession session = sessionByEntitlementId.get(ent.getId());
                if (session == null && ent.isUsed() && !legacySessions.isEmpty())
                    session = legacySessions.pollFirst();

                if (session != null) {
                    dto.setSessionId(session.getId());
                    if (session.getStatus() == SessionStatus.IN_PROGRESS) {
                        dto.setStatus("IN_PROGRESS");
                    } else {
                        dto.setStatus("COMPLETED");
                        HrResult r = resultsByAttempt.get(session.getAttemptNumber());
                        if (r != null) dto.setTimedOut(r.isTimedOut());
                    }
                } else if (ent.isUsed()) {
                    // Used, but no session could be matched to it (a gap in legacy
                    // data predating the direct entitlement<->session link). Never
                    // show this as PENDING regardless — offering a "Start" button
                    // that start() would then reject as "already started" is worse
                    // than an assignment whose in-between state we can't fully
                    // reconstruct. Best-effort label it done.
                    dto.setStatus("COMPLETED");
                } else {
                    dto.setStatus("PENDING");
                }
                out.add(dto);
            }
        }
        return out;
    }

    /* ── Start a fresh attempt at one SPECIFIC assignment ────────────
       Keyed by entitlementId, not by pillar type — a person can be assigned
       the same pillar more than once (different buyers/orders), each its own
       independent assignment (see listAssignments()). Starting must act on
       exactly the assignment the caller picked, never "whichever entitlement
       of this pillar happens to be oldest/unused" — that silently started the
       wrong assignment whenever more than one of the same pillar was pending
       at once. ─────────────────────────────────────────────────────────── */
    @Transactional
    public HrStartAssessmentResponse start(UUID entitlementId, String email) {
        User user = requireUser(email);
        Entitlement entitlement = entitlementRepo.findById(entitlementId)
                .filter(e -> e.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        if (entitlement.isUsed())
            throw new AccessDeniedException("This assignment has already been started.");
        if (!entitlement.getProductCode().startsWith(PRODUCT_PREFIX))
            throw new IllegalArgumentException("Assignment not found");

        short assessmentId = assessmentIdFromProductCode(entitlement.getProductCode());
        HrAssessment assessment = requireAssessment(assessmentId);
        String productCode = entitlement.getProductCode();

        List<String> questionIds = generateQuestionIds(assessment);

        int attemptNumber = sessionRepo.findByUserAndProductCodeOrderByAttemptNumberDesc(user, productCode)
                .stream().findFirst().map(s -> s.getAttemptNumber() + 1).orElse(1);

        AssessmentSession session = new AssessmentSession();
        session.setUser(user);
        session.setProductCode(productCode);
        session.setAttemptNumber(attemptNumber);
        session.setQuestionIds(questionIds);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setEntitlement(entitlement);
        session = sessionRepo.save(session);

        entitlement.setUsed(true);
        entitlementRepo.save(entitlement);

        HrStartAssessmentResponse res = new HrStartAssessmentResponse();
        res.setSessionId(session.getId());
        res.setAttemptNumber(session.getAttemptNumber());
        res.setQuestions(toOrderedQuestionDtos(questionIds, session.getId()));
        res.setDeadlineAt(session.getDeadlineAt());
        return res;
    }

    /* ── The caller's in-progress session for one pillar, if any (resume-
       after-login) — mirrors AssessmentService.current(), parametrized by
       pillar since HR has 7 independent product codes instead of Leader's
       one. ─────────────────────────────────────────────────────────── */
    @Transactional
    public Optional<HrSessionResumeResponse> current(short assessmentId, String email) {
        User user = requireUser(email);
        return sessionRepo
                .findFirstByUserAndProductCodeAndStatusOrderByStartedAtDesc(
                        user, productCodeFor(assessmentId), SessionStatus.IN_PROGRESS)
                .map(session -> resume(session.getId(), email));
    }

    /* ── Resume: current questions + what's already answered ─────── */
    @Transactional
    public HrSessionResumeResponse resume(UUID sessionId, String email) {
        AssessmentSession session = requireOwnedHrSession(sessionId, email);
        if (session.getStatus() == SessionStatus.IN_PROGRESS && SessionTimer.applyResumeGrace(session))
            session = sessionRepo.save(session);

        Map<String, String> answered = answerRepo.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> String.valueOf(a.getSelectedOption())));

        HrSessionResumeResponse res = new HrSessionResumeResponse();
        HrAssessment assessment = requireAssessment(assessmentIdFromProductCode(session.getProductCode()));
        res.setSessionId(session.getId());
        res.setAssessmentId(assessment.getId());
        res.setAssessmentName(assessment.getName());
        res.setAttemptNumber(session.getAttemptNumber());
        res.setStatus(session.getStatus().name());
        res.setQuestions(toOrderedQuestionDtos(session.getQuestionIds(), session.getId()));
        res.setAnsweredOptions(answered);
        res.setDeadlineAt(session.getDeadlineAt());
        return res;
    }

    /* ── Every in-progress session across all 7 pillars — for the unified
       /dashboard, which shows HR activity alongside Leader/Org/Pulse rather
       than on a separate page. One query instead of 7 per-pillar lookups. ── */
    @Transactional
    public List<HrSessionResumeResponse> listInProgress(String email) {
        User user = requireUser(email);
        List<HrSessionResumeResponse> out = new ArrayList<>();
        for (AssessmentSession session : sessionRepo.findByUserAndStatusAndProductCodeStartingWith(
                user, SessionStatus.IN_PROGRESS, PRODUCT_PREFIX)) {
            try {
                out.add(resume(session.getId(), email));
            } catch (RuntimeException e) {
                // One unresumable session (e.g. its question_ids reference questions
                // no longer in the bank) must not 500 the whole /dashboard aggregate.
                log.warn("Skipping unresumable in-progress HR session {} for {}: {}",
                        session.getId(), email, e.toString());
            }
        }
        return out;
    }

    /* ── Autosave one answer (upsert) ─────────────────────────────── */
    @Transactional
    public AnswerResponse answer(UUID sessionId, String email, HrAnswerRequest req) {
        AssessmentSession session = requireOwnedHrSession(sessionId, email);
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw new IllegalStateException("This assessment has already been submitted");
        // Time's up: once the deadline has passed the assessment is over and no further
        // answers are accepted. A small grace absorbs the last autosave racing the final
        // forced submit the client fires when its own countdown hits zero.
        if (session.getDeadlineAt() != null
                && LocalDateTime.now().isAfter(session.getDeadlineAt().plusSeconds(20)))
            throw new IllegalStateException("The time for this assessment has ended.");
        if (!session.getQuestionIds().contains(req.getQuestionId()))
            throw new IllegalArgumentException("Question is not part of this session");

        HrQuestionBank question = hrQuestionBankRepo.findById(req.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown question: " + req.getQuestionId()));

        // The client only ever sees/sends the *displayed* letter (post-shuffle) — translate
        // back to the original A-E the scores are actually keyed by. See OptionOrder.
        char displayed = req.getSelectedOption().charAt(0);
        char original = OptionOrder.toOriginal(req.getQuestionId(), session.getId(), displayed, HR_OPTION_COUNT);
        short score = question.scoreFor(original);

        Answer answer = answerRepo.findBySessionIdAndQuestionId(sessionId, req.getQuestionId())
                .orElseGet(Answer::new);
        answer.setSession(session);
        answer.setQuestionId(req.getQuestionId());
        answer.setSelectedOption(displayed);
        answer.setScore(score);
        answerRepo.save(answer);

        int answeredCount = answerRepo.findBySessionId(sessionId).size();

        AnswerResponse res = new AnswerResponse();
        res.setQuestionId(req.getQuestionId());
        res.setSelectedOption(req.getSelectedOption());
        res.setAnsweredCount(answeredCount);
        // Count against what the player actually renders — see renderableQuestionIds.
        res.setTotalQuestions(renderableQuestionIds(session.getQuestionIds()).size());
        return res;
    }

    /* ── Submit: score, roll up competency/skill-category, persist ── */
    @Transactional
    public HrResultResponse submit(UUID sessionId, String email) {
        AssessmentSession session = requireOwnedHrSession(sessionId, email);
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw new IllegalStateException("This assessment has already been submitted");

        List<Answer> answers = answerRepo.findBySessionId(sessionId);
        // Measure completion against the questions the player could actually answer, not
        // the raw session list — a stale id the bank no longer has is never rendered, so
        // it must not block submission (that would strand the respondent with no fix).
        int total = renderableQuestionIds(session.getQuestionIds()).size();
        // Once the server's own clock says the deadline has passed, the sitting is over:
        // answer() is already refusing new answers, so blocking the submit here would just
        // strand the respondent. The bypass depends only on timeExpired (server-authoritative,
        // a client cannot fake it) — `forced` is irrelevant to it.
        boolean timeExpired = session.getDeadlineAt() != null && LocalDateTime.now().isAfter(session.getDeadlineAt());
        if (answers.size() < total && !timeExpired)
            throw new IllegalArgumentException(
                    "Answer all " + total + " questions before submitting (" + answers.size() + " answered)");
        // Ran out of time with questions still unanswered — the result stands, marked as a
        // timeout, and the overall percentage is "marks achieved so far" out of the full
        // paper (every unanswered question counts as zero).
        boolean timedOut = timeExpired && answers.size() < total;

        Map<String, HrQuestionBank> questionsById = hrQuestionBankRepo
                .findByQuestionIdIn(answers.stream().map(Answer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(HrQuestionBank::getQuestionId, q -> q));

        Set<String> competencyCodes = questionsById.values().stream()
                .map(HrQuestionBank::getCompetencyCode).collect(Collectors.toSet());
        Map<String, HrCompetency> competenciesByCode = hrCompetencyRepo.findAllById(competencyCodes).stream()
                .collect(Collectors.toMap(HrCompetency::getCode, c -> c));

        Set<Integer> skillCategoryIds = competenciesByCode.values().stream()
                .map(HrCompetency::getSkillCategoryId).collect(Collectors.toSet());
        Map<Integer, String> skillCategoryNames = hrSkillCategoryRepo.findAllById(skillCategoryIds).stream()
                .collect(Collectors.toMap(HrSkillCategory::getId, HrSkillCategory::getName));

        // Raw sums + how many questions contributed to each, so every rollup below can be
        // expressed as a percentage of *its own* max (competencies/skill-categories don't
        // all draw the same number of questions, so a shared denominator would be unfair).
        Map<String, Integer> competencyScoreSum = new LinkedHashMap<>();
        Map<String, Integer> competencyCount = new LinkedHashMap<>();
        Map<String, Integer> skillCategoryScoreSum = new LinkedHashMap<>();
        Map<String, Integer> skillCategoryCount = new LinkedHashMap<>();

        int overallSum = 0;
        Answer strongestAnswer = null;
        Answer weakestAnswer = null;

        for (Answer a : answers) {
            HrQuestionBank q = questionsById.get(a.getQuestionId());
            String competencyCode = q.getCompetencyCode();
            competencyScoreSum.merge(competencyCode, (int) a.getScore(), Integer::sum);
            competencyCount.merge(competencyCode, 1, Integer::sum);

            String skillCategoryName = skillCategoryNames.get(competenciesByCode.get(competencyCode).getSkillCategoryId());
            skillCategoryScoreSum.merge(skillCategoryName, (int) a.getScore(), Integer::sum);
            skillCategoryCount.merge(skillCategoryName, 1, Integer::sum);

            overallSum += a.getScore();
            if (strongestAnswer == null || a.getScore() > strongestAnswer.getScore()) strongestAnswer = a;
            if (weakestAnswer == null || a.getScore() < weakestAnswer.getScore()) weakestAnswer = a;
        }

        Map<String, Integer> competencyScores = toPercentages(competencyScoreSum, competencyCount);
        Map<String, Integer> skillCategoryScores = toPercentages(skillCategoryScoreSum, skillCategoryCount);

        HrResult result = new HrResult();
        result.setSession(session);
        result.setUser(session.getUser());
        result.setAssessment(requireAssessment(assessmentIdFromProductCode(session.getProductCode())));
        result.setAttemptNumber(session.getAttemptNumber());
        result.setOverallScore((short) percentage(overallSum, timedOut ? total : answers.size()));
        result.setTimedOut(timedOut);
        result.setCompetencyScores(competencyScores);
        result.setSkillCategoryScores(skillCategoryScores);
        if (strongestAnswer != null) result.setStrongestCompetency(questionsById.get(strongestAnswer.getQuestionId()).getCompetencyCode());
        if (weakestAnswer != null) result.setWeakestCompetency(questionsById.get(weakestAnswer.getQuestionId()).getCompetencyCode());
        result = hrResultRepo.save(result);

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setTimedOut(timedOut);
        sessionRepo.save(session);

        // A submitted test is the natural end of this sitting — clear the
        // single-session flag so this account isn't stuck unable to log back in
        // just because the candidate closed the browser instead of logging out
        // (see AuthService.enforceSingleSession()).
        User submitter = session.getUser();
        submitter.setSessionActive(false);
        userRepo.save(submitter);

        return toResponse(result);
    }

    /* ── Attempt history, all 7 pillars combined ───────────────────── */
    @Transactional(readOnly = true)
    public List<HrResultResponse> listResults(String email) {
        User user = requireUser(email);
        return hrResultRepo.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse).toList();
    }

    /* ── Selection algorithm ─────────────────────────────────────────
       For each competency in the assessment, draw min_random_selection
       distinct active questions from that competency's own pool. Then fill
       the remainder ("balance") with distinct active questions drawn from
       anywhere in the assessment that wasn't already picked. Finally
       shuffle the combined set so competency order isn't detectable from
       question order. ─────────────────────────────────────────────── */
    // Package-private (not private) so HrAssessmentServiceTest can exercise the
    // selection algorithm directly rather than only indirectly through start().
    List<String> generateQuestionIds(HrAssessment assessment) {
        List<HrCompetency> competencies = hrCompetencyRepo.findByAssessmentId(assessment.getId());
        List<String> questionIds = new ArrayList<>(assessment.getQuestionCount());
        Set<String> chosen = new HashSet<>();

        for (HrCompetency competency : competencies) {
            List<HrQuestionBank> pool = hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue(competency.getCode());
            if (pool.size() < competency.getMinRandomSelection())
                throw new IllegalStateException("Not enough active questions for competency " + competency.getCode()
                        + " (need " + competency.getMinRandomSelection() + ", have " + pool.size() + ")");

            shuffle(pool);
            for (int i = 0; i < competency.getMinRandomSelection(); i++) {
                String questionId = pool.get(i).getQuestionId();
                questionIds.add(questionId);
                chosen.add(questionId);
            }
        }

        int balance = assessment.getQuestionCount() - questionIds.size();
        if (balance > 0) {
            List<String> competencyCodes = competencies.stream().map(HrCompetency::getCode).toList();
            List<HrQuestionBank> remaining = hrQuestionBankRepo
                    .findByCompetencyCodeInAndActiveTrue(competencyCodes).stream()
                    .filter(q -> !chosen.contains(q.getQuestionId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (remaining.size() < balance)
                throw new IllegalStateException("Not enough remaining active questions in assessment "
                        + assessment.getId() + " to fill the balance (need " + balance + ", have " + remaining.size() + ")");

            shuffle(remaining);
            for (int i = 0; i < balance; i++) questionIds.add(remaining.get(i).getQuestionId());
        } else if (balance < 0) {
            // Shouldn't happen given the verified taxonomy data, but this is a
            // configuration error worth failing loudly on rather than silently
            // truncating to question_count.
            throw new IllegalStateException("Assessment " + assessment.getId()
                    + ": sum of competency min_random_selection exceeds question_count");
        }

        shuffle(questionIds);
        return questionIds;
    }

    /* ── Private helpers ───────────────────────────────────────── */

    /** Rounds a raw score sum to a 0-100 percentage of what was achievable
     *  ({@code questionCount * MAX_SCORE_PER_QUESTION}). Zero questions -> 0, not NaN.
     *  Clamped to [0, 100] so a stray extra answer or a bad score row can never
     *  surface a percentage above 100. */
    private static int percentage(int scoreSum, int questionCount) {
        if (questionCount == 0) return 0;
        int pct = Math.round(scoreSum * 100f / (questionCount * MAX_SCORE_PER_QUESTION));
        return Math.max(0, Math.min(100, pct));
    }

    private static Map<String, Integer> toPercentages(Map<String, Integer> scoreSums, Map<String, Integer> counts) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : scoreSums.entrySet()) {
            out.put(e.getKey(), percentage(e.getValue(), counts.get(e.getKey())));
        }
        return out;
    }

    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }

    private static String productCodeFor(short assessmentId) {
        return PRODUCT_PREFIX + assessmentId;
    }

    private static short assessmentIdFromProductCode(String productCode) {
        return Short.parseShort(productCode.substring(PRODUCT_PREFIX.length()));
    }

    /**
     * The subset of a session's question ids that still resolve to a bank row — i.e.
     * exactly what {@link #toOrderedQuestionDtos} renders to the player. Ids the
     * session references but the bank no longer has (e.g. after a question-bank
     * reload) are not part of the assessment as far as the respondent can tell,
     * so completion / submission must be measured against this, not the raw list.
     */
    private List<String> renderableQuestionIds(List<String> sessionQuestionIds) {
        Set<String> present = hrQuestionBankRepo.findByQuestionIdIn(sessionQuestionIds).stream()
                .map(HrQuestionBank::getQuestionId).collect(Collectors.toSet());
        // distinct: answers are one-per-question, so a repeated id in the stored list
        // would otherwise inflate the target and permanently block submission.
        return sessionQuestionIds.stream().filter(present::contains).distinct().toList();
    }

    private List<HrQuestionDto> toOrderedQuestionDtos(List<String> ids, UUID sessionId) {
        Map<String, HrQuestionBank> byId = hrQuestionBankRepo.findByQuestionIdIn(ids).stream()
                .collect(Collectors.toMap(HrQuestionBank::getQuestionId, q -> q, (a, b) -> a));
        // Skip ids no longer in the bank rather than NPE — a session built against an
        // older question bank can still be listed (and partially resumed) instead of
        // taking down every caller of resume().
        List<String> missing = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty())
            log.warn("Session {} references {} question id(s) not in hr_question_bank: {}",
                    sessionId, missing.size(), missing);
        return ids.stream().map(byId::get).filter(Objects::nonNull)
                .map(q -> toQuestionDto(q, sessionId)).toList();
    }

    private HrQuestionDto toQuestionDto(HrQuestionBank q, UUID sessionId) {
        char[] order = OptionOrder.displayOrder(q.getQuestionId(), sessionId, HR_OPTION_COUNT);
        HrQuestionDto dto = new HrQuestionDto();
        dto.setQuestionId(q.getQuestionId());
        dto.setText(q.getText());
        dto.setOptionA(optionText(q, order[0]));
        dto.setOptionB(optionText(q, order[1]));
        dto.setOptionC(optionText(q, order[2]));
        dto.setOptionD(optionText(q, order[3]));
        dto.setOptionE(optionText(q, order[4]));
        return dto;
    }

    private String optionText(HrQuestionBank q, char original) {
        return switch (original) {
            case 'A' -> q.getOptionA();
            case 'B' -> q.getOptionB();
            case 'C' -> q.getOptionC();
            case 'D' -> q.getOptionD();
            case 'E' -> q.getOptionE();
            default -> throw new IllegalArgumentException("Invalid option: " + original);
        };
    }

    /**
     * The test-taker's view of one of their own HR attempts — deliberately
     * SCORE-FREE. HR is a buyer-arranged product: the person who took the test
     * sees only that it was completed (assessment, attempt, date); the scored
     * breakdown is visible solely to the buyer, via
     * {@link HrOrderService#listMyCandidates} / the "Candidates You've Registered"
     * dashboard card. Do not add score fields here.
     */
    private HrResultResponse toResponse(HrResult r) {
        HrResultResponse dto = new HrResultResponse();
        dto.setId(r.getId());
        dto.setSessionId(r.getSession().getId());

        HrAssessment assessment = r.getAssessment();
        dto.setAssessmentId(assessment.getId());
        dto.setAssessmentCode(assessment.getCode());
        dto.setAssessmentName(assessment.getName());

        dto.setAttemptNumber(r.getAttemptNumber());
        dto.setTimedOut(r.isTimedOut());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }

    /** Same generic session table Leader/Pulse use, so a session's productCode must
     *  actually be one of ours (HR_A1..HR_A7) — otherwise a Leader/Pulse session ID
     *  could be resumed/answered through the HR endpoints by mistake. */
    private AssessmentSession requireOwnedHrSession(UUID sessionId, String email) {
        AssessmentSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment session not found"));
        if (!session.getUser().getEmail().equalsIgnoreCase(email) || !session.getProductCode().startsWith(PRODUCT_PREFIX))
            throw new IllegalArgumentException("Assessment session not found");
        return session;
    }

    private HrAssessment requireAssessment(short assessmentId) {
        return hrAssessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown assessment: " + assessmentId));
    }

    private User requireUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
