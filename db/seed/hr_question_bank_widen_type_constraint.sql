-- Widens hr_question_bank's question_type check constraint to allow FIB, the
-- third HR question type (Fill-in-the-Blank) — the constraint currently only
-- allows MCQ and SJT. Run this BEFORE the hr_question_bank_seed_part*.sql
-- files (hr_taxonomy_seed.sql can run before or after — it doesn't touch
-- this table).

BEGIN;

ALTER TABLE hr_question_bank DROP CONSTRAINT hr_question_bank_question_type_check;
ALTER TABLE hr_question_bank ADD CONSTRAINT hr_question_bank_question_type_check
  CHECK (question_type::text = ANY (ARRAY['FIB','MCQ','SJT']::text[]));

COMMIT;
