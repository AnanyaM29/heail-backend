-- ─────────────────────────────────────────────────────────────────────────────
-- Promote every existing account to ORG_ADMIN, superadmins excepted.
-- Pairs with the code change that makes every NEW account (self-registration,
-- HR candidate throwaway accounts, pulse-respondent invites) get created as
-- ORG_ADMIN from now on — see AuthService.register(), HrOrderService.
-- fulfilOneCandidate(), OrgOrderService.fulfil().
--
-- Ownership of orders/sessions/entitlements is enforced by email everywhere in
-- the backend, independent of role, so this changes what an account is
-- LABELLED, not what it can act on.
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE users
SET role = 'ORG_ADMIN'
WHERE role IS DISTINCT FROM 'SUPERADMIN';
