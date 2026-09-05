-- HEAIL HR assessment taxonomy seed
-- Generated from HEAIL_Master.xlsx + "Directions for HR assessment creation (1).xlsx".
-- Idempotent: safe to re-run, upserts on primary key.
-- Prerequisite: hr_assessment / hr_skill_category / hr_competency tables must already
-- exist (created by Hibernate ddl-auto=update the first time the app starts).
-- Run this before either part of hr_question_bank_seed_part*.sql (question rows
-- reference competency codes seeded here via a foreign key-like column).
--
-- Regenerated 2026-09 from the revised "Directions_for_HR_assessment_creation (1).xlsx" +
-- "Final_HR_Questions (2).xlsx" — min_random_selection changed for 15 of the 46
-- competencies vs the prior version (values are the sum of that competency's
-- FIB + MCQ + SJT minimums in the Directions sheet).

BEGIN;

-- ── HR assessments (the 7 pillars) ──────────────────────────
INSERT INTO hr_assessment (id, code, name, question_count, time_minutes) VALUES
  (1, $qb$HR_A1$qb$, $qb$Adaptability, Transformation and Agility$qb$, 30, 30),
  (2, $qb$HR_A2$qb$, $qb$Critical Thinking, Problem Solving and Decision making$qb$, 30, 30),
  (3, $qb$HR_A3$qb$, $qb$Communication, Interpersonal and Conflict resolution skills$qb$, 30, 30),
  (4, $qb$HR_A4$qb$, $qb$Culture, Ethics, Diversity and Collaboration$qb$, 30, 30),
  (5, $qb$HR_A5$qb$, $qb$Leadership, Strategic Alignment and Stakeholder management$qb$, 30, 30),
  (6, $qb$HR_A6$qb$, $qb$Empathy, Resilience, Accountability and Integrity$qb$, 30, 30),
  (7, $qb$HR_A7$qb$, $qb$Planning, Execution and Productivity$qb$, 30, 30)
ON CONFLICT (id) DO UPDATE SET
  code = EXCLUDED.code,
  name = EXCLUDED.name,
  question_count = EXCLUDED.question_count,
  time_minutes = EXCLUDED.time_minutes;

-- ── Skill categories (12) ─────────────────────────────
INSERT INTO hr_skill_category (id, assessment_id, name) VALUES
  (1, 1, $qb$Adaptability & Future Mindset$qb$),
  (2, 1, $qb$Adaptability & Mindset$qb$),
  (3, 2, $qb$Cognitive & Analytical Mastery$qb$),
  (4, 2, $qb$Problem Solving & Decision Making$qb$),
  (5, 3, $qb$Communication & Interpersonal$qb$),
  (6, 4, $qb$Culture, Ethics & Diversity$qb$),
  (7, 4, $qb$Interpersonal Synergy & Feedback$qb$),
  (8, 5, $qb$Leadership & Strategic Management$qb$),
  (9, 5, $qb$Leadership & Team Dynamics$qb$),
  (10, 6, $qb$Personal Integrity & Resilience$qb$),
  (11, 7, $qb$Planning & Execution$qb$),
  (12, 7, $qb$Productivity & Self-Leadership$qb$)
ON CONFLICT (id) DO UPDATE SET
  assessment_id = EXCLUDED.assessment_id,
  name = EXCLUDED.name;

-- ── Competencies (46) — min_random_selection is the sum of the
--    FIB+MCQ+SJT minimums for that competency from the Directions sheet ─────
INSERT INTO hr_competency (code, assessment_id, skill_category_id, name, min_random_selection) VALUES
  ($qb$A1-01$qb$, 1, 1, $qb$Human-AI Synergy & Co-Intelligence$qb$, 4),
  ($qb$A1-02$qb$, 1, 1, $qb$Tolerance for Ambiguity$qb$, 3),
  ($qb$A1-03$qb$, 1, 1, $qb$Unlearning Agility$qb$, 3),
  ($qb$A1-04$qb$, 1, 2, $qb$Mindfulness & Emotional Presence$qb$, 3),
  ($qb$A1-05$qb$, 1, 2, $qb$Supervisor & Alignment Management$qb$, 4),
  ($qb$A1-06$qb$, 1, 2, $qb$Team Dynamics Integration$qb$, 3),
  ($qb$A1-07$qb$, 1, 2, $qb$Technology & AI Literacy$qb$, 3),
  ($qb$A1-08$qb$, 1, 2, $qb$Transformative Mindset & Agility$qb$, 4),
  ($qb$A1-09$qb$, 1, 2, $qb$Workplace & Change Adaptation$qb$, 3),
  ($qb$A2-01$qb$, 2, 3, $qb$First-Principles Thinking$qb$, 4),
  ($qb$A2-02$qb$, 2, 3, $qb$Information Synthesis & Signal Filtering$qb$, 5),
  ($qb$A2-03$qb$, 2, 3, $qb$Systems Thinking$qb$, 5),
  ($qb$A2-04$qb$, 2, 4, $qb$Critical Thinking$qb$, 5),
  ($qb$A2-05$qb$, 2, 4, $qb$Decision-Making Under Uncertainty$qb$, 5),
  ($qb$A2-06$qb$, 2, 4, $qb$Problem Identification & Innovation$qb$, 6),
  ($qb$A3-01$qb$, 3, 5, $qb$Active & Patient Listening$qb$, 5),
  ($qb$A3-02$qb$, 3, 5, $qb$Communication Skills$qb$, 5),
  ($qb$A3-03$qb$, 3, 5, $qb$Conflict Resolution & De-escalation$qb$, 5),
  ($qb$A3-04$qb$, 3, 5, $qb$Language & Cross-Cultural Sensitivity$qb$, 5),
  ($qb$A3-05$qb$, 3, 5, $qb$Negotiation Skills$qb$, 5),
  ($qb$A3-06$qb$, 3, 5, $qb$Persuasive & Influential Communication$qb$, 5),
  ($qb$A4-01$qb$, 4, 6, $qb$Egoless & Accountability Mindset$qb$, 5),
  ($qb$A4-02$qb$, 4, 6, $qb$Ethical Working & Professional Integrity$qb$, 5),
  ($qb$A4-03$qb$, 4, 6, $qb$Inclusivity & Equal Opportunity Orientation$qb$, 4),
  ($qb$A4-04$qb$, 4, 6, $qb$Workplace Gratitude & Constructive Mindset$qb$, 5),
  ($qb$A4-05$qb$, 4, 7, $qb$Asynchronous Collaboration & Digital Etiquette$qb$, 4),
  ($qb$A4-06$qb$, 4, 7, $qb$Business Storytelling & Narrative Building$qb$, 3),
  ($qb$A4-07$qb$, 4, 7, $qb$Feedback Receptivity & Delivery$qb$, 4),
  ($qb$A5-01$qb$, 5, 8, $qb$Delegation & Empowerment$qb$, 5),
  ($qb$A5-02$qb$, 5, 8, $qb$People Management & Coaching$qb$, 7),
  ($qb$A5-03$qb$, 5, 8, $qb$Stakeholder Management$qb$, 4),
  ($qb$A5-04$qb$, 5, 8, $qb$Vision & Strategic Alignment$qb$, 5),
  ($qb$A5-05$qb$, 5, 9, $qb$Cross-Functional Silo-Busting$qb$, 5),
  ($qb$A5-06$qb$, 5, 9, $qb$Psychological Safety Creation$qb$, 4),
  ($qb$A6-01$qb$, 6, 10, $qb$Accountability & Ownership$qb$, 6),
  ($qb$A6-02$qb$, 6, 10, $qb$Continuous Learning & Growth Mindset$qb$, 6),
  ($qb$A6-03$qb$, 6, 10, $qb$Emotional Intelligence & Empathy$qb$, 6),
  ($qb$A6-04$qb$, 6, 10, $qb$Self-Motivation & Enterprise$qb$, 6),
  ($qb$A6-05$qb$, 6, 10, $qb$Working Under Pressure & Stress Resilience$qb$, 6),
  ($qb$A7-01$qb$, 7, 11, $qb$Execution & Closure Focus$qb$, 4),
  ($qb$A7-02$qb$, 7, 11, $qb$Precision & Written Documentation$qb$, 4),
  ($qb$A7-03$qb$, 7, 11, $qb$Proactive Strategic Planning$qb$, 5),
  ($qb$A7-04$qb$, 7, 11, $qb$Resourcefulness & Operational Ingenuity$qb$, 5),
  ($qb$A7-05$qb$, 7, 11, $qb$Time Management & Punctuality$qb$, 4),
  ($qb$A7-06$qb$, 7, 12, $qb$Attention & Focus Management$qb$, 5),
  ($qb$A7-07$qb$, 7, 12, $qb$Energy Management & Burnout Prevention$qb$, 3)
ON CONFLICT (code) DO UPDATE SET
  assessment_id = EXCLUDED.assessment_id,
  skill_category_id = EXCLUDED.skill_category_id,
  name = EXCLUDED.name,
  min_random_selection = EXCLUDED.min_random_selection;

COMMIT;
