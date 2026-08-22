package com.example.heail_backend.service;

import com.example.heail_backend.dto.HrResultResponse;
import com.example.heail_backend.dto.HrStartAssessmentResponse;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HrAssessmentServiceTest {

    private final EntitlementRepository entitlementRepo = mock(EntitlementRepository.class);
    private final AssessmentSessionRepository sessionRepo = mock(AssessmentSessionRepository.class);
    private final AnswerRepository answerRepo = mock(AnswerRepository.class);
    private final HrAssessmentRepository hrAssessmentRepo = mock(HrAssessmentRepository.class);
    private final HrSkillCategoryRepository hrSkillCategoryRepo = mock(HrSkillCategoryRepository.class);
    private final HrCompetencyRepository hrCompetencyRepo = mock(HrCompetencyRepository.class);
    private final HrQuestionBankRepository hrQuestionBankRepo = mock(HrQuestionBankRepository.class);
    private final HrResultRepository hrResultRepo = mock(HrResultRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);

    private final HrAssessmentService service = new HrAssessmentService(
            entitlementRepo, sessionRepo, answerRepo, hrAssessmentRepo, hrSkillCategoryRepo,
            hrCompetencyRepo, hrQuestionBankRepo, hrResultRepo, userRepo);

    private static final String EMAIL = "candidate@example.com";

    /* ── Selection algorithm ─────────────────────────────────────── */

    @Test
    void selectionDrawsExactlyQuestionCountDistinctQuestionsRespectingPerCompetencyMinimums() {
        HrAssessment assessment = assessment((short) 1, 10);
        HrCompetency c1 = competency("C1", (short) 1, 1, 3);
        HrCompetency c2 = competency("C2", (short) 1, 2, 4);
        when(hrCompetencyRepo.findByAssessmentId((short) 1)).thenReturn(List.of(c1, c2));

        List<HrQuestionBank> c1Pool = questionPool("C1", 5);
        List<HrQuestionBank> c2Pool = questionPool("C2", 6);
        when(hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue("C1")).thenReturn(c1Pool);
        when(hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue("C2")).thenReturn(c2Pool);
        when(hrQuestionBankRepo.findByCompetencyCodeInAndActiveTrue(List.of("C1", "C2")))
                .thenReturn(concat(c1Pool, c2Pool));

        List<String> result = service.generateQuestionIds(assessment);

        assertThat(result).hasSize(10);
        assertThat(result).doesNotHaveDuplicates();
        long fromC1 = result.stream().filter(id -> id.startsWith("C1-")).count();
        long fromC2 = result.stream().filter(id -> id.startsWith("C2-")).count();
        assertThat(fromC1).isGreaterThanOrEqualTo(3);
        assertThat(fromC2).isGreaterThanOrEqualTo(4);
        assertThat(fromC1 + fromC2).isEqualTo(10);
    }

    @Test
    void selectionThrowsWhenACompetencyDoesNotHaveEnoughActiveQuestions() {
        HrAssessment assessment = assessment((short) 1, 10);
        HrCompetency c1 = competency("C1", (short) 1, 1, 5);
        when(hrCompetencyRepo.findByAssessmentId((short) 1)).thenReturn(List.of(c1));
        when(hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue("C1")).thenReturn(questionPool("C1", 3));

        assertThatThrownBy(() -> service.generateQuestionIds(assessment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C1");
    }

    @Test
    void selectionThrowsWhenTheBalancePoolCannotFillTheRemainder() {
        HrAssessment assessment = assessment((short) 1, 10);
        // Both competencies' pools are exactly their minimum — nothing left over to draw the balance from.
        HrCompetency c1 = competency("C1", (short) 1, 1, 3);
        HrCompetency c2 = competency("C2", (short) 1, 2, 4);
        when(hrCompetencyRepo.findByAssessmentId((short) 1)).thenReturn(List.of(c1, c2));

        List<HrQuestionBank> c1Pool = questionPool("C1", 3);
        List<HrQuestionBank> c2Pool = questionPool("C2", 4);
        when(hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue("C1")).thenReturn(c1Pool);
        when(hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue("C2")).thenReturn(c2Pool);
        when(hrQuestionBankRepo.findByCompetencyCodeInAndActiveTrue(List.of("C1", "C2")))
                .thenReturn(concat(c1Pool, c2Pool));

        assertThatThrownBy(() -> service.generateQuestionIds(assessment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("balance");
    }

    /* ── Entitlement gating ─────────────────────────────────────── */

    @Test
    void startWithoutAnUnusedEntitlementIsDenied() {
        User user = user(EMAIL);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hrAssessmentRepo.findById((short) 1)).thenReturn(Optional.of(assessment((short) 1, 3)));
        when(entitlementRepo.findFirstByUserAndProductCodeAndUsedFalseOrderByCreatedAtAsc(user, "HR_A1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start((short) 1, EMAIL))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void startConsumesTheEntitlementAndBuildsASessionOfTheRightSize() {
        User user = user(EMAIL);
        HrAssessment assessment = assessment((short) 1, 3);
        HrCompetency c1 = competency("C1", (short) 1, 1, 3);
        List<HrQuestionBank> pool = questionPool("C1", 3); // exactly the minimum — balance is 0, no extra mocking needed
        Entitlement entitlement = new Entitlement();
        entitlement.setUser(user);
        entitlement.setProductCode("HR_A1");
        entitlement.setUsed(false);

        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(hrAssessmentRepo.findById((short) 1)).thenReturn(Optional.of(assessment));
        when(entitlementRepo.findFirstByUserAndProductCodeAndUsedFalseOrderByCreatedAtAsc(user, "HR_A1"))
                .thenReturn(Optional.of(entitlement));
        when(hrCompetencyRepo.findByAssessmentId((short) 1)).thenReturn(List.of(c1));
        when(hrQuestionBankRepo.findByCompetencyCodeAndActiveTrue("C1")).thenReturn(pool);
        when(hrQuestionBankRepo.findByQuestionIdIn(anyList())).thenReturn(pool);
        when(sessionRepo.findByUserAndProductCodeOrderByAttemptNumberDesc(user, "HR_A1")).thenReturn(List.of());
        when(sessionRepo.save(any())).thenAnswer(inv -> {
            AssessmentSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        HrStartAssessmentResponse res = service.start((short) 1, EMAIL);

        assertThat(res.getAttemptNumber()).isEqualTo(1);
        assertThat(res.getQuestions()).hasSize(3);
        assertThat(entitlement.isUsed()).isTrue();
        verify(entitlementRepo).save(entitlement);
    }

    /* ── Scoring ────────────────────────────────────────────────── */

    @Test
    void submitSumsScoresPerCompetencyAndSkillCategoryAndPicksStrongestWeakest() {
        User user = user(EMAIL);
        UUID sessionId = UUID.randomUUID();

        AssessmentSession session = new AssessmentSession();
        session.setId(sessionId);
        session.setUser(user);
        session.setProductCode("HR_A1");
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setAttemptNumber(1);
        session.setQuestionIds(List.of("C1-Q1", "C2-Q1"));
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Answer strong = answer(sessionId, "C1-Q1", (short) 5);
        Answer weak = answer(sessionId, "C2-Q1", (short) 2);
        when(answerRepo.findBySessionId(sessionId)).thenReturn(List.of(strong, weak));

        HrQuestionBank q1 = questionPool("C1", 1).get(0); // id "C1-Q1"
        HrQuestionBank q2 = questionPool("C2", 1).get(0); // id "C2-Q1"
        when(hrQuestionBankRepo.findByQuestionIdIn(anyList())).thenReturn(List.of(q1, q2));

        HrCompetency c1 = competency("C1", (short) 1, 1, 3);
        HrCompetency c2 = competency("C2", (short) 1, 2, 4);
        when(hrCompetencyRepo.findAllById(anySet())).thenReturn(List.of(c1, c2));

        HrSkillCategory sc1 = skillCategory(1, "Cognitive Agility");
        HrSkillCategory sc2 = skillCategory(2, "Problem Solving");
        when(hrSkillCategoryRepo.findAllById(anySet())).thenReturn(List.of(sc1, sc2));

        when(hrAssessmentRepo.findById((short) 1)).thenReturn(Optional.of(assessment((short) 1, 2)));
        when(hrResultRepo.save(any())).thenAnswer(inv -> {
            HrResult r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(java.time.LocalDateTime.now());
            return r;
        });

        HrResultResponse res = service.submit(sessionId, EMAIL);

        assertThat(res.getOverallScore()).isEqualTo(7);
        assertThat(res.getCompetencyScores()).containsEntry("C1", 5).containsEntry("C2", 2);
        assertThat(res.getSkillCategoryScores()).containsEntry("Cognitive Agility", 5).containsEntry("Problem Solving", 2);
        assertThat(res.getStrongestCompetency()).isEqualTo("C1");
        assertThat(res.getWeakestCompetency()).isEqualTo("C2");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    /* ── Fixtures ───────────────────────────────────────────────── */

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setName("Test Candidate");
        return u;
    }

    private HrAssessment assessment(short id, int questionCount) {
        HrAssessment a = new HrAssessment();
        a.setId(id);
        a.setCode("PILLAR_" + id);
        a.setName("Pillar " + id);
        a.setQuestionCount((short) questionCount);
        a.setTimeMinutes((short) 30);
        return a;
    }

    private HrCompetency competency(String code, short assessmentId, int skillCategoryId, int minRandomSelection) {
        HrCompetency c = new HrCompetency();
        c.setCode(code);
        c.setAssessmentId(assessmentId);
        c.setSkillCategoryId(skillCategoryId);
        c.setName(code + " name");
        c.setMinRandomSelection((short) minRandomSelection);
        return c;
    }

    private HrSkillCategory skillCategory(int id, String name) {
        HrSkillCategory sc = new HrSkillCategory();
        sc.setId(id);
        sc.setName(name);
        return sc;
    }

    private List<HrQuestionBank> questionPool(String competencyCode, int count) {
        List<HrQuestionBank> pool = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            HrQuestionBank q = new HrQuestionBank();
            q.setQuestionId(competencyCode + "-Q" + i);
            q.setCompetencyCode(competencyCode);
            q.setQuestionType("MCQ");
            q.setText("Question " + i);
            q.setOptionA("A"); q.setOptionB("B"); q.setOptionC("C"); q.setOptionD("D"); q.setOptionE("E");
            q.setScoreA((short) 5); q.setScoreB((short) 3); q.setScoreC((short) 2); q.setScoreD((short) 1); q.setScoreE((short) 0);
            q.setActive(true);
            pool.add(q);
        }
        return pool;
    }

    private Answer answer(UUID sessionId, String questionId, short score) {
        Answer a = new Answer();
        a.setId(UUID.randomUUID());
        a.setQuestionId(questionId);
        a.setSelectedOption('A');
        a.setScore(score);
        return a;
    }

    private List<HrQuestionBank> concat(List<HrQuestionBank> a, List<HrQuestionBank> b) {
        List<HrQuestionBank> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
