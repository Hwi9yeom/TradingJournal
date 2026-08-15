-- Goal planning fields: horizon (this year / 5y / 10y / ultimate) + written commitments.
-- Mirrors the long-horizon goal sheet: 목표 기간 구분, 스스로에게 하는 약속,
-- 목표 달성한 날 할 일, 목표 달성 이후 계획.
--
-- Scope: H2 / PostgreSQL only (the `mysql` profile disables Flyway and owns its
-- schema in db/mysql/schema-mysql.sql, which declares `horizon` as a native
-- MySQL ENUM to satisfy Hibernate's MySQLDialect validation).

ALTER TABLE goals ADD COLUMN IF NOT EXISTS horizon VARCHAR(20);
ALTER TABLE goals ADD COLUMN IF NOT EXISTS commitment VARCHAR(1000);
ALTER TABLE goals ADD COLUMN IF NOT EXISTS reward_plan VARCHAR(1000);
ALTER TABLE goals ADD COLUMN IF NOT EXISTS post_achievement_plan VARCHAR(1000);

-- Existing rows predate the horizon concept; treat them as this-year goals so the
-- NOT NULL constraint below can be applied without data loss.
UPDATE goals SET horizon = 'THIS_YEAR' WHERE horizon IS NULL;

ALTER TABLE goals ALTER COLUMN horizon SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_goal_horizon ON goals(horizon);
