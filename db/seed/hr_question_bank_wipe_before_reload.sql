-- Wipes the HR question bank before a full content reload from a revised
-- source workbook ("Final_HR_Questions (2).xlsx"). Run this ONCE, before the
-- 7 hr_question_bank_seed_part*_of_7_A*.sql files below.
--
-- No FK constraint references hr_question_bank (competency_code / question_id
-- are plain reference columns, not enforced relations — see the entity
-- comments), so this is safe to run standalone. It does orphan question_id
-- values already recorded in `answers` and in the question_ids array on old
-- assessment_session rows for COMPLETED attempts — harmless, nothing re-reads
-- those. Any session currently IN_PROGRESS against the old question set will
-- no longer be resumable (HrAssessmentService.resume skips missing questions
-- rather than erroring, but the attempt itself can't be completed as
-- originally drawn) — run this when no HR assessment is mid-attempt, or
-- accept that in-flight attempts will need to be abandoned and restarted.

BEGIN;
DELETE FROM hr_question_bank;
COMMIT;
