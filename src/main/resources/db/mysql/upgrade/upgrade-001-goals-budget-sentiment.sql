-- =============================================================================
-- MySQL upgrade script for EXISTING installations (pre goals/budget/sentiment).
--
-- Why this file exists: the `mysql` profile disables Flyway and applies
-- schema-mysql.sql only when the Docker volume is first created. Existing
-- databases therefore never receive new columns/tables and Hibernate's
-- ddl-auto=validate fails at startup after upgrading the application.
--
-- Fresh installs do NOT need this file - schema-mysql.sql already contains
-- everything below.
--
-- Run ONCE against an existing database, e.g.:
--   mysql -u tradingjournal -p tradingjournal \
--     < src/main/resources/db/mysql/upgrade/upgrade-001-goals-budget-sentiment.sql
--
-- NOTE: MySQL does not support ADD COLUMN IF NOT EXISTS; running this script
-- twice will fail on the goals ALTERs (harmless - the schema is already
-- upgraded at that point). CREATE TABLE statements are idempotent.
-- =============================================================================

-- --- goals: horizon + written commitments (mirrors Flyway V8) ---------------

ALTER TABLE goals ADD COLUMN commitment varchar(1000);
ALTER TABLE goals ADD COLUMN reward_plan varchar(1000);
ALTER TABLE goals ADD COLUMN post_achievement_plan varchar(1000);
ALTER TABLE goals ADD COLUMN horizon enum ('FIVE_YEAR','TEN_YEAR','THIS_YEAR','ULTIMATE');

UPDATE goals SET horizon = 'THIS_YEAR' WHERE horizon IS NULL;

ALTER TABLE goals MODIFY COLUMN horizon enum ('FIVE_YEAR','TEN_YEAR','THIS_YEAR','ULTIMATE') NOT NULL;

CREATE INDEX idx_goal_horizon ON goals (horizon);

-- --- monthly_budgets (mirrors Flyway V9) -------------------------------------

CREATE TABLE IF NOT EXISTS monthly_budgets (
    budget_month date not null,
    user_id bigint,
    account_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    actual_savings TEXT,
    fixed_expense TEXT,
    fixed_income TEXT,
    net_worth TEXT,
    planned_savings TEXT,
    variable_expense TEXT,
    variable_income TEXT,
    notes varchar(2000),
    primary key (id),
    constraint uk_budget_user_account_month unique (user_id, account_id, budget_month)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

CREATE INDEX idx_budget_month ON monthly_budgets (budget_month);
CREATE INDEX idx_budget_account ON monthly_budgets (account_id);
CREATE INDEX idx_budget_user ON monthly_budgets (user_id);

-- --- savings_records (mirrors Flyway V10) ------------------------------------

CREATE TABLE IF NOT EXISTS savings_records (
    saved_date date not null,
    user_id bigint,
    account_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    amount TEXT not null,
    institution varchar(100),
    memo varchar(1000),
    category enum ('DEBT_REPAYMENT','EMERGENCY_FUND','INVESTMENT_TRANSFER','OTHER','PENSION','REGULAR_SAVINGS') not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

CREATE INDEX idx_savings_date ON savings_records (saved_date);
CREATE INDEX idx_savings_account ON savings_records (account_id);
CREATE INDEX idx_savings_category ON savings_records (category);
CREATE INDEX idx_savings_user ON savings_records (user_id);

-- --- market_sentiments (mirrors Flyway V11) ----------------------------------

CREATE TABLE IF NOT EXISTS market_sentiments (
    recorded_date date not null,
    indicator_value decimal(19,4) not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    notes varchar(1000),
    indicator enum ('AAII_BEARISH','AAII_BULLISH','CRYPTO_FEAR_GREED','FEAR_GREED_INDEX','FUNDING_RATE','LONG_SHORT_RATIO','MVRV_Z_SCORE','NAAIM_EXPOSURE','PUELL_MULTIPLE','PUT_CALL_RATIO','RHODL_RATIO','SMART_DUMB_MONEY') not null,
    primary key (id),
    constraint uk_sentiment_indicator_date unique (indicator, recorded_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

CREATE INDEX idx_sentiment_date ON market_sentiments (recorded_date);
CREATE INDEX idx_sentiment_indicator ON market_sentiments (indicator);
