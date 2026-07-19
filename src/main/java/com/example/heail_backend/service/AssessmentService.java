package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";
    private static final List<String> PRINCIPLE_CODES =
            IntStream.rangeClosed(1, 50).mapToObj(i -> String.format("P%02d", i)).toList();

    private final EntitlementRepository entitlementRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final AnswerRepository answerRepo;
    private final LeaderQuestionBankRepository questionBankRepo;
    private final LeaderResultRepository leaderResultRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    /* ── Start a fresh attempt ─────────────────────────────────── */
    @Transactional
    public StartAssessmentResponse start(String email) {
        User user = requireUser(email);

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
        res.setQuestions(toOrderedQuestionDtos(questionIds));
        return res;
    }

    /* ── The caller's in-progress session, if any (resume-after-login) ── */
    @Transactional(readOnly = true)
    public Optional<SessionResumeResponse> current(String email) {
        User user = requireUser(email);
        return sessionRepo
                .findFirstByUserAndProductCodeAndStatusOrderByStartedAtDesc(user, LEADER_CLASSIC_PRODUCT, SessionStatus.IN_PROGRESS)
                .map(session -> resume(session.getId(), email));
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
            throw new IllegalStateException("This assessment has already been submitted");
        if (!session.getQuestionIds().contains(req.getQuestionId()))
            throw new IllegalArgumentException("Question is not part of this session");

        LeaderQuestionBank question = questionBankRepo.findById(req.getQuestionId())
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

    /* ── Submit: score, band, persist, no approval gate ───────────── */
    @Transactional
    public LeaderResultResponse submit(UUID sessionId, String email) {
        AssessmentSession session = requireOwnedSession(sessionId, email);
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw new IllegalStateException("This assessment has already been submitted");

        List<Answer> answers = answerRepo.findBySessionId(sessionId);
        int total = session.getQuestionIds().size();
        if (answers.size() < total)
            throw new IllegalArgumentException(
                    "Answer all " + total + " questions before submitting (" + answers.size() + " answered)");

        Map<String, LeaderQuestionBank> questionsById = questionBankRepo
                .findByQuestionIdIn(answers.stream().map(Answer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(LeaderQuestionBank::getQuestionId, q -> q));

        Map<String, Integer> domainScores = new LinkedHashMap<>();
        for (String d : List.of("I", "II", "III", "IV", "V")) domainScores.put(d, 0);

        int overall = 0;
        Answer strongestAnswer = null;
        Answer weakestAnswer = null;

        for (Answer a : answers) {
            LeaderQuestionBank q = questionsById.get(a.getQuestionId());
            domainScores.merge(q.getDomain(), (int) a.getScore(), Integer::sum);
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
        result.setStrongestPrinciple(questionsById.get(strongestAnswer.getQuestionId()).getPrincipleCode());
        result.setWeakestPrinciple(questionsById.get(weakestAnswer.getQuestionId()).getPrincipleCode());
        result = leaderResultRepo.save(result);

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepo.save(session);

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

    private LeaderBand bandFor(int overall) {
        if (overall >= 200) return LeaderBand.MASTER;
        if (overall >= 160) return LeaderBand.ADVANCED;
        if (overall >= 120) return LeaderBand.DEVELOPING;
        if (overall >= 80) return LeaderBand.EMERGING;
        return LeaderBand.BEGINNING;
    }

    private List<QuestionDto> toOrderedQuestionDtos(List<String> ids) {
        Map<String, LeaderQuestionBank> byId = questionBankRepo.findByQuestionIdIn(ids).stream()
                .collect(Collectors.toMap(LeaderQuestionBank::getQuestionId, q -> q));
        return ids.stream().map(id -> toQuestionDto(byId.get(id))).toList();
    }

    private QuestionDto toQuestionDto(LeaderQuestionBank q) {
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(q.getQuestionId());
        dto.setText(q.getText());
        dto.setOptionA(q.getOptionA());
        dto.setOptionB(q.getOptionB());
        dto.setOptionC(q.getOptionC());
        dto.setOptionD(q.getOptionD());
        return dto;
    }

    private LeaderResultResponse toResponse(LeaderResult r) {
        LeaderResultResponse dto = new LeaderResultResponse();
        dto.setId(r.getId());
        dto.setSessionId(r.getSession().getId());
        dto.setAttemptNumber(r.getAttemptNumber());
        dto.setOverallScore(r.getOverallScore());
        dto.setBand(r.getBand().name());
        dto.setDomainScores(r.getDomainScores());
        dto.setStrongestPrinciple(r.getStrongestPrinciple());
        dto.setWeakestPrinciple(r.getWeakestPrinciple());
        dto.setCreatedAt(r.getCreatedAt());

        Map<String, String> principleTexts = principleTexts(r.getSession().getId(), r.getStrongestPrinciple(), r.getWeakestPrinciple());
        dto.setStrongestPrincipleText(principleTexts.get(r.getStrongestPrinciple()));
        dto.setWeakestPrincipleText(principleTexts.get(r.getWeakestPrinciple()));
        return dto;
    }

    /* ── Full question text for the given principle codes, as answered in this session ── */
    private Map<String, String> principleTexts(UUID sessionId, String... principleCodes) {
        List<Answer> answers = answerRepo.findBySessionId(sessionId);
        Map<String, LeaderQuestionBank> questionsById = questionBankRepo
                .findByQuestionIdIn(answers.stream().map(Answer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(LeaderQuestionBank::getQuestionId, q -> q));

        Set<String> wanted = Set.of(principleCodes);
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
