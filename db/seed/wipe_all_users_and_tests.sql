-- ═════════════════════════════════════════════════════════════════════════════
-- DESTRUCTIVE — irreversible on the shared production database.
-- BACK UP FIRST (pg_dump) if there is anything here worth keeping.
--
-- Removes, across Leader / HR / Pulse alike:
--   - every assessment session, answer, and result (leader_results, hr_result,
--     section_scores, org_section_results)
--   - every order, entitlement, HR candidate, org employee, consent record,
--     refresh token
--   - every user account EXCEPT role = 'SUPERADMIN'
--   - every organisation no longer referenced by a remaining (superadmin) user
--
-- Deliberately NOT touched (content/catalog, not test/attempt data):
--   hr_question_bank, hr_competency, hr_skill_category, hr_assessment,
--   leader_question_bank, leader_principle, question_bank, sections,
--   email_template, discount_coupons, pricing_items, otp_tokens,
--   contact_messages, partner_applications
--
-- Test/result rows are deleted unconditionally (not filtered by user role) —
-- so even a superadmin's own test history is cleared, while the superadmin
-- USER row itself survives, per the request.
-- ═════════════════════════════════════════════════════════════════════════════

BEGIN;

-- Attempt-level detail (children of assessment_session)
DELETE FROM answers;
DELETE FROM section_scores;
DELETE FROM hr_result;
DELETE FROM leader_results;

-- Sessions themselves
DELETE FROM assessment_session;

-- Org-round rollups and per-candidate/employee records
DELETE FROM org_section_results;
DELETE FROM consent_log;
-- Leftover from the retired candidate-reallocation feature — the JPA entity is
-- gone from the codebase, but ddl-auto=update never drops old tables, and this
-- one still carries a live FK into hr_candidates.
DELETE FROM hr_candidate_requests;
DELETE FROM hr_candidates;
DELETE FROM order_employees;

-- Entitlements and orders
DELETE FROM entitlements;
DELETE FROM orders;

-- Sessions/tokens for accounts about to be removed
DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM users WHERE role IS DISTINCT FROM 'SUPERADMIN');

-- Every account except superadmins
DELETE FROM users WHERE role IS DISTINCT FROM 'SUPERADMIN';

-- Organisations no longer referenced by any remaining user
DELETE FROM organisations
WHERE id NOT IN (SELECT organisation_id FROM users WHERE organisation_id IS NOT NULL);

COMMIT;
