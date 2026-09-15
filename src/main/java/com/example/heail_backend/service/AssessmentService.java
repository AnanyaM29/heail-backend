package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import com.example.heail_backend.util.OptionOrder;
import com.example.heail_backend.util.SessionTimer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";
    private static final List<String> PRINCIPLE_CODES =
            IntStream.rangeClosed(1, 50).mapToObj(i -> String.format("P%02d", i)).toList();
    private static final int MAX_SCORE_PER_QUESTION = 5;

    private final EntitlementRepository entitlementRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final AnswerRepository answerRepo;
    private final LeaderQuestionBankRepository questionBankRepo;
    private final LeaderResultRepository leaderResultRepo;
    private final LeaderPrincipleRepository leaderPrincipleRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    /* ── Start a fresh attempt ─────────────────────────────────── */
    @Transactional
    public StartAssessmentResponse start(String email) {
        User user = requireUser(email);

        // Every purchased entitlement gets its own independent attempt — its own
        // fresh question draw, its own 30-minute deadline from the moment THIS
        // start() call runs. Reusing an older in-progress session here (an earlier
        // attempt at this) meant paying for a new sitting and clicking "Start
        // Assessment" could hand back a stale, half-answered, nearly-timed-out
        // session instead of the fresh one just paid for. An abandoned older
        // session isn't lost — it's still reachable/resumable on its own and will
        // simply time itself out (see submit()'s timedOut handling) if left alone.

        Entitlement entitlement = entitlementRepo
                .findFirstByUserAndProductCodeAndUsedFalseOrderByCreatedAtAsc(user, LEADER_CLASSIC_PRODUCT)
                .orElseThrow(() -> new AccessDeniedException("No unused entitlement for The Gita Leader assessment"));

        List<String> questionIds = new ArrayList<>(PRINCIPLE_CODES.size());
        for (String principleCode : PRINCIPLE_CODES) {
            List<LeaderQuestionBank> candidates = questionBankRepo.findByPrincipleCodeAndActiveTrue(principleCode);
            if (candidates.isEmpty())
                throw new IllegalStateException("No active question available for principle " + principleCode);
            LeaderQuestionBank chosen = candidates.get(secureRandom.nextInt(candidates.size()));
            questionIds.add(chosen.getQuestionId());
        }
        shuffle(questionIds);

        int attemptNumber = sessionRepo.findByUserAndProductCodeOrderByAttemptNumberDesc(user, LEADER_CLASSIC_PRODUCT)
                .stream().findFirst().map(s -> s.getAttemptNumber() + 1).orElse(1);

        AssessmentSession session = new AssessmentSession();
        session.setUser(user);
        session.setProductCode(LEADER_CLASSIC_PRODUCT);
        session.setAttemptNumber(attemptNumber);
        session.setQuestionIds(questionIds);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session = sessionRepo.save(session);

        entitlement.setUsed(true);
        entitlementRepo.save(entitlement);

        StartAssessmentResponse res = new StartAssessmentResponse();
        res.setSessionId(session.getId());
        res.setAttemptNumber(session.getAttemptNumber());
        res.setQuestions(toOrderedQuestionDtos(questionIds, session.getId()));
        res.setDeadlineAt(session.getDeadlineAt());
        return res;
    }

    /* ── Whether the caller currently holds an unused entitlement — checked
       proactively by the dashboard so it doesn't show "Start Assessment"
       before payment has actually gone through. ─────────────────────── */
    @Transactional(readOnly = true)
    public boolean hasEntitlement(String email) {
        User user = requireUser(email);
        return entitlementRepo
                .findFirstByUserAndProductCodeAndUsedFalseOrderByCreatedAtAsc(user, LEADER_CLASSIC_PRODUCT)
                .isPresent();
    }

    /* ── The caller's in-progress session, if any (resume-after-login) ──
       Not readOnly: delegates to resume() via a plain (non-proxied) self-call,
       which may need to persist a resume-grace deadline extension — a readOnly
       transaction here would silently swallow that write. ─────────────── */
    @Transactional
    public Optional<SessionResumeResponse> current(String email) {
        User user = requireUser(email);
        return sessionRepo
                .findFirstByUserAndProductCodeAndStatusOrderByStartedAtDesc(user, LEADER_CLASSIC_PRODUCT, SessionStatus.IN_PROGRESS)
                .map(session -> resume(session.getId(), email));
    }

    /* ── Every unfinished attempt, not just the most recent — since start()
       gives each purchase its own independent session, more than one can be
       IN_PROGRESS at once (e.g. a new purchase started while an older attempt
       was left unfinished). The unified dashboard shows each as its own card
       so none of them are invisible. ─────────────────────────────────────── */
    @Transactional
    public List<SessionResumeResponse> listInProgress(String email) {
        User user = requireUser(email);
        return sessionRepo.findByUserAndProductCodeOrderByAttemptNumberDesc(user, LEADER_CLASSIC_PRODUCT).stream()
                .filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS)
                .sorted(Comparator.comparingInt(AssessmentSession::getAttemptNumber))
                .map(s -> resume(s.getId(), email))
                .toList();
    }

    /* ── Resume: current questions + what's already answered ─────── */
    @Transactional
    public SessionResumeResponse resume(UUID sessionId, String email) {
        AssessmentSession session = requireOwnedSession(sessionId, email);
        if (session.getStatus() == SessionStatus.IN_PROGRESS && SessionTimer.applyResumeGrace(session))
            session = sessionRepo.save(session);

        Map<String, String> answered = answerRepo.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> String.valueOf(a.getSelectedOption())));

        SessionResumeResponse res = new SessionResumeResponse();
        res.setSessionId(session.getId());
        res.setAttemptNumber(session.getAttemptNumber());
        res.setStatus(session.getStatus().name());
        res.setQuestions(toOrderedQuestionDtos(session.getQuestionIds(), session.getId()));
        res.setAnsweredOptions(answered);
        res.setDeadlineAt(session.getDeadlineAt());
        res.setStartedAt(session.getStartedAt());
        return res;
    }

    /* ── Autosave one answer (upsert) ─────────────────────────────── */
    @Transactional
    public AnswerResponse answer(UUID sessionId, String email, AnswerRequest req) {
        AssessmentSession session = requireOwnedSession(sessionId, email);
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

        LeaderQuestionBank question = questionBankRepo.findById(req.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown question: " + req.getQuestionId()));

        // The client only ever sees/sends the *displayed* letter (post-shuffle) — translate
        // back to the original A/B/C/D the scores are actually keyed by. See OptionOrder.
        char displayed = req.getSelectedOption().charAt(0);
        char original = OptionOrder.toOriginal(req.getQuestionId(), session.getId(), displayed);
        short score = question.scoreFor(original);

        Answer answer = answerRepo.findBySessionIdAndQuestionId(sessionId, req.getQuestionId())
                .orElseGet(Answer::new);
        answer.setSession(session);
        answer.setQuestionId(req.getQuestionId());
        answer.setSelectedOption(displayed);
        answer.setScore(score);
        answerRepo.save(answer);

        // Distinct: see the duplicate-row race noted in submit() — the same possible
        // double-insert would otherwise show an inflated "51 of 50 answered" mid-test.
        int answeredCount = (int) answerRepo.findBySessionId(sessionId).stream()
                .map(Answer::getQuestionId).distinct().count();

        AnswerResponse res = new AnswerResponse();
        res.setQuestionId(req.getQuestionId());
        res.setSelectedOption(req.getSelectedOption());
        res.setAnsweredCount(answeredCount);
        res.setTotalQuestions((int) session.getQuestionIds().stream().distinct().count());
        return res;
    }

    /* ── Submit: score, band, persist, no approval gate ───────────── */
    @Transactional
    public LeaderResultResponse submit(UUID sessionId, String email, boolean forced) {
        AssessmentSession session = requireOwnedSession(sessionId, email);
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw new IllegalStateException("This assessment has already been submitted");

        // answer()'s upsert (findBySessionIdAndQuestionId, then insert-or-update) has no
        // DB-level uniqueness backing it — two autosave calls for the same question landing
        // close together (a fast re-click, a client retry after a slow response) can each
        // miss the other's not-yet-committed row and both insert, leaving two Answer rows
        // for one question. Collapsing to the most recently answered row per question here
        // makes scoring correct regardless of whether that race ever produced duplicates,
        // rather than trusting answerRepo.findBySessionId() to already be one-per-question.
        List<Answer> answers = answerRepo.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> a,
                        (a, b) -> a.getAnsweredAt().isAfter(b.getAnsweredAt()) ? a : b))
                .values().stream().toList();
        // Distinct ids: answers are one-per-question (upsert), so if the stored id list
        // ever carries a repeat, comparing against the raw size would make the count
        // unreachable and permanently reject the submit.
        int total = (int) session.getQuestionIds().stream().distinct().count();
        // Once the server's own clock says the deadline has passed, the sitting is over:
        // answer() is already refusing new answers, so blocking the submit here would just
        // strand the taker. The bypass depends only on timeExpired (server-authoritative,
        // a client cannot fake it) — `forced` is irrelevant to it.
        boolean timeExpired = session.getDeadlineAt() != null && LocalDateTime.now().isAfter(session.getDeadlineAt());
        if (answers.size() < total && !timeExpired)
            throw new IllegalArgumentException(
                    "Answer all " + total + " questions before submitting (" + answers.size() + " answered)");
        // Ran out of time with questions still unanswered — the result stands, marked as a
        // timeout; the percentage reported is marks achieved out of the full paper.
        boolean timedOut = timeExpired && answers.size() < total;

        Map<String, LeaderQuestionBank> questionsById = questionBankRepo
                .findByQuestionIdIn(answers.stream().map(Answer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(LeaderQuestionBank::getQuestionId, q -> q, (a, b) -> a));

        // A principle is meant to belong to exactly one domain, but that domain is stored
        // per QUESTION ROW, not per principle — and a principle has several candidate
        // question variants (only one of which gets randomly drawn into any given
        // session). If a variant was ever tagged with the wrong domain during data entry,
        // scoring by q.getDomain() lets which domain a principle counts toward drift from
        // one random draw to the next, so a domain's question count (and therefore its max
        // achievable score) is no longer reliably 10 questions / 50 points. Resolving
        // domain by majority vote across ALL of a principle's variants makes the
        // assignment fixed and attempt-independent, and domainMax below is derived from
        // that same resolution — so the two can never disagree.
        Map<String, String> principleDomains = principleDomains();
        Map<String, Integer> domainMax = domainMaxFor(principleDomains);

        Map<String, Integer> domainScores = new LinkedHashMap<>();
        for (String d : List.of("I", "II", "III", "IV", "V")) domainScores.put(d, 0);

        int overall = 0;
        Answer strongestAnswer = null;
        Answer weakestAnswer = null;

        for (Answer a : answers) {
            LeaderQuestionBank q = questionsById.get(a.getQuestionId());
            // An answer whose question is no longer in the bank (bank reloaded mid-flight)
            // simply doesn't contribute — it must not NPE the whole submit and strand the taker.
            if (q == null) continue;
            String domain = principleDomains.get(q.getPrincipleCode());
            if (domain != null) domainScores.merge(domain, (int) a.getScore(), Integer::sum);
            overall += a.getScore();
            if (strongestAnswer == null || a.getScore() > strongestAnswer.getScore()) strongestAnswer = a;
            if (weakestAnswer == null || a.getScore() < weakestAnswer.getScore()) weakestAnswer = a;
        }

        LeaderResult result = new LeaderResult();
        result.setSession(session);
        result.setUser(session.getUser());
        result.setAttemptNumber(session.getAttemptNumber());
        result.setOverallScore((short) overall);
        result.setBand(bandFor(overall));
        result.setDomainScores(domainScores);
        result.setDomainMax(domainMax);
        result.setTimedOut(timedOut);
        if (strongestAnswer != null) result.setStrongestPrinciple(questionsById.get(strongestAnswer.getQuestionId()).getPrincipleCode());
        if (weakestAnswer != null) result.setWeakestPrinciple(questionsById.get(weakestAnswer.getQuestionId()).getPrincipleCode());
        result = leaderResultRepo.save(result);

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

        emailService.sendLeaderResultsReady(session.getUser().getEmail(), session.getUser().getName());

        return toResponse(result);
    }

    /* ── Attempt history ───────────────────────────────────────── */
    @Transactional(readOnly = true)
    public List<LeaderResultResponse> listResults(String email) {
        User user = requireUser(email);
        return leaderResultRepo.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse).toList();
    }

    /* ── Private helpers ───────────────────────────────────────── */
    private void shuffle(List<String> ids) {
        for (int i = ids.size() - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            Collections.swap(ids, i, j);
        }
    }

    /** Canonical domain for each of the 50 principles, resolved by majority vote
     *  across that principle's own question variants — see the comment at its call
     *  site in submit() for why a per-answer q.getDomain() read isn't safe. */
    private Map<String, String> principleDomains() {
        Map<String, Map<String, Long>> votes = new HashMap<>();
        for (LeaderQuestionBank q : questionBankRepo.findAll()) {
            votes.computeIfAbsent(q.getPrincipleCode(), k -> new HashMap<>())
                    .merge(q.getDomain(), 1L, Long::sum);
        }
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> e : votes.entrySet()) {
            e.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(top -> out.put(e.getKey(), top.getKey()));
        }
        return out;
    }

    /** Achievable max per domain — purely a function of the question bank's
     *  principle→domain resolution, never of which questions one taker happened to
     *  get, so it's identical for every attempt past or future. */
    private Map<String, Integer> domainMaxFor(Map<String, String> principleDomains) {
        Map<String, Integer> domainMax = new LinkedHashMap<>();
        for (String d : List.of("I", "II", "III", "IV", "V")) domainMax.put(d, 0);
        for (String d : principleDomains.values())
            domainMax.merge(d, MAX_SCORE_PER_QUESTION, Integer::sum);
        return domainMax;
    }

    private LeaderBand bandFor(int overall) {
        if (overall >= 200) return LeaderBand.MASTER;
        if (overall >= 160) return LeaderBand.ADVANCED;
        if (overall >= 120) return LeaderBand.DEVELOPING;
        if (overall >= 80) return LeaderBand.EMERGING;
        return LeaderBand.BEGINNING;
    }

    private List<QuestionDto> toOrderedQuestionDtos(List<String> ids, UUID sessionId) {
        Map<String, LeaderQuestionBank> byId = questionBankRepo.findByQuestionIdIn(ids).stream()
                .collect(Collectors.toMap(LeaderQuestionBank::getQuestionId, q -> q));
        return ids.stream().map(id -> toQuestionDto(byId.get(id), sessionId)).toList();
    }

    private QuestionDto toQuestionDto(LeaderQuestionBank q, UUID sessionId) {
        char[] order = OptionOrder.displayOrder(q.getQuestionId(), sessionId);
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(q.getQuestionId());
        dto.setText(q.getText());
        dto.setOptionA(optionText(q, order[0]));
        dto.setOptionB(optionText(q, order[1]));
        dto.setOptionC(optionText(q, order[2]));
        dto.setOptionD(optionText(q, order[3]));
        return dto;
    }

    private String optionText(LeaderQuestionBank q, char original) {
        return switch (original) {
            case 'A' -> q.getOptionA();
            case 'B' -> q.getOptionB();
            case 'C' -> q.getOptionC();
            case 'D' -> q.getOptionD();
            default -> throw new IllegalArgumentException("Invalid option: " + original);
        };
    }

    private LeaderResultResponse toResponse(LeaderResult r) {
        LeaderResultResponse dto = new LeaderResultResponse();
        dto.setId(r.getId());
        dto.setSessionId(r.getSession().getId());
        dto.setAttemptNumber(r.getAttemptNumber());
        dto.setOverallScore(r.getOverallScore());
        dto.setBand(r.getBand().name());
        dto.setTimedOut(r.isTimedOut());
        dto.setDomainScores(r.getDomainScores());
        // domainMax wasn't persisted on results scored before this field existed —
        // but it depends only on the question bank, never on the specific attempt,
        // so it's safe (and correct) to compute it live for those rows too.
        dto.setDomainMax(r.getDomainMax() != null ? r.getDomainMax() : domainMaxFor(principleDomains()));
        dto.setStrongestPrinciple(r.getStrongestPrinciple());
        dto.setWeakestPrinciple(r.getWeakestPrinciple());
        dto.setCreatedAt(r.getCreatedAt());

        // Prefer the principle's own name (leader_principle table); fall back to the
        // wording of the question the person actually answered for it if that code
        // has no name row yet.
        Map<String, String> principleNames = leaderPrincipleRepo.findAllById(
                        Stream.of(r.getStrongestPrinciple(), r.getWeakestPrinciple())
                                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(LeaderPrinciple::getCode, LeaderPrinciple::getName));
        Map<String, String> principleTexts = principleTexts(r.getSession().getId(), r.getStrongestPrinciple(), r.getWeakestPrinciple());
        dto.setStrongestPrincipleText(principleNames.getOrDefault(r.getStrongestPrinciple(), principleTexts.get(r.getStrongestPrinciple())));
        dto.setWeakestPrincipleText(principleNames.getOrDefault(r.getWeakestPrinciple(), principleTexts.get(r.getWeakestPrinciple())));
        return dto;
    }

    /* ── Full question text for the given principle codes, as answered in this session ── */
    private Map<String, String> principleTexts(UUID sessionId, String... principleCodes) {
        List<Answer> answers = answerRepo.findBySessionId(sessionId);
        Map<String, LeaderQuestionBank> questionsById = questionBankRepo
                .findByQuestionIdIn(answers.stream().map(Answer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(LeaderQuestionBank::getQuestionId, q -> q));

        Set<String> wanted = Arrays.stream(principleCodes).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> result = new HashMap<>();
        for (Answer a : answers) {
            LeaderQuestionBank q = questionsById.get(a.getQuestionId());
            if (q != null && wanted.contains(q.getPrincipleCode())) result.put(q.getPrincipleCode(), q.getText());
        }
        return result;
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
