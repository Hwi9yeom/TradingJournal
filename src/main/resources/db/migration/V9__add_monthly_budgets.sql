-- Monthly household check-up: fixed/variable income, fixed/variable expense,
-- planned vs actual savings, and a net-worth snapshot per month.
-- Feeds the savings-capacity view and SAVINGS_AMOUNT goal tracking.
--
-- Scope: H2 / PostgreSQL only (the `mysql` profile disables Flyway and owns its
-- schema in db/mysql/schema-mysql.sql).
--
-- Money columns are TEXT because they are stored encrypted via
-- EncryptedBigDecimalConverter, exactly like portfolios/transactions amounts.
-- Consequence: no DB-level SUM/AVG on these columns.

CREATE TABLE IF NOT EXISTS monthly_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT,
    budget_month DATE NOT NULL,
    fixed_income TEXT,
    variable_income TEXT,
    fixed_expense TEXT,
    variable_expense TEXT,
    planned_savings TEXT,
    actual_savings TEXT,
    net_worth TEXT,
    notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_budget_account_month UNIQUE (account_id, budget_month)
);

CREATE INDEX IF NOT EXISTS idx_budget_month ON monthly_budgets(budget_month);
CREATE INDEX IF NOT EXISTS idx_budget_account ON monthly_budgets(account_id);
