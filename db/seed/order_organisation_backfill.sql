-- ─────────────────────────────────────────────────────────────────────────────
-- Backfill orders.organisation_id for rounds created before Order.organisation
-- existed. Without this, an already-PAID/completed round keeps resolving its
-- displayed company name through the buyer account's CURRENT organisation —
-- which a later, unrelated round set up under the same account can rename,
-- retroactively changing the name shown on this old round's dashboard cards,
-- report, and reminder emails too.
--
-- setOrgDetails() only ever populates organisation_id going forward (it only
-- runs on a DRAFT order), so existing paid orders need this one-time backfill.
-- It freezes each such order to whatever its buyer's organisation is named
-- RIGHT NOW — if that name has already been overwritten by a later round under
-- the same account, the original historical name is unfortunately not
-- recoverable from this table; this at least stops further drift.
--
-- Safe to re-run: only touches rows that don't already have their own org.
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE orders o
SET organisation_id = u.organisation_id
FROM users u
WHERE o.user_id = u.id
  AND o.organisation_id IS NULL
  AND u.organisation_id IS NOT NULL;
