-- Adds blacklist/soft-delete moderation columns to users.
-- Hibernate's ddl-auto=update will also create these automatically on next
-- boot, but this script lets them be applied explicitly/ahead of time.

ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blacklisted_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
