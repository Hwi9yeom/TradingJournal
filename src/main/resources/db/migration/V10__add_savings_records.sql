-- Savings journal: one row per deposit/transfer into savings or investment.
-- Monthly sums feed monthly_budgets.actual_savings, so the monthly figure stops
-- being hand-maintained once individual entries exist.
--
-- Scope: H2 / PostgreSQL only (the `mysql` profile disables Flyway and owns its
-- schema in db/mysql/schema-mysql.sql).
--
-- amount is TEXT because it is stored encrypted via EncryptedBigDecimalConverter.

CREATE TABLE IF NOT EXISTS savings_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT,
    account_id BIGINT,
    saved_date DATE NOT NULL,
    amount TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    institution VARCHAR(100),
    memo VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_savings_date ON savings_records(saved_date);
CREATE INDEX IF NOT EXISTS idx_savings_account ON savings_records(account_id);
CREATE INDEX IF NOT EXISTS idx_savings_category ON savings_records(category);
CREATE INDEX IF NOT EXISTS idx_savings_user ON savings_records(user_id);
