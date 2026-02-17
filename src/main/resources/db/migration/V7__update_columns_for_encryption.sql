-- V7: Update column types for encryption support
-- Encrypted values are stored as Base64 strings, requiring TEXT type

-- Note: This migration runs on both H2 and PostgreSQL
-- H2: Uses CLOB for TEXT
-- PostgreSQL: Uses TEXT natively

-- IMPORTANT: Fields used in aggregate functions (AVG, SUM) must remain numeric
-- realizedPnl and rMultiple are NOT encrypted because they are used in AVG queries

-- Transaction table changes
ALTER TABLE transactions ALTER COLUMN price TYPE TEXT;
ALTER TABLE transactions ALTER COLUMN quantity TYPE TEXT;
ALTER TABLE transactions ALTER COLUMN commission TYPE TEXT;
-- realized_pnl stays numeric (used in AVG queries)
ALTER TABLE transactions ALTER COLUMN cost_basis TYPE TEXT;

-- Portfolio table changes
ALTER TABLE portfolios ALTER COLUMN quantity TYPE TEXT;
ALTER TABLE portfolios ALTER COLUMN average_price TYPE TEXT;
ALTER TABLE portfolios ALTER COLUMN total_investment TYPE TEXT;

-- Account table changes
ALTER TABLE accounts ALTER COLUMN name TYPE TEXT;
