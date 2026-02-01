-- Database Index Optimization Migration
-- This script adds optimized indexes based on query analysis

-- Stocks table indexes (if not exists from entity annotations)
CREATE INDEX IF NOT EXISTS idx_stock_symbol_perf ON stocks(symbol);
CREATE INDEX IF NOT EXISTS idx_stock_name_search ON stocks(name);
CREATE INDEX IF NOT EXISTS idx_stock_exchange_filter ON stocks(exchange);

-- Transactions table composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_transaction_stock_date_perf ON transactions(stock_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_type_date_perf ON transactions(type, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_date_range ON transactions(transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_audit_created ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transaction_audit_updated ON transactions(updated_at);

-- Portfolio table indexes for quick lookups
CREATE INDEX IF NOT EXISTS idx_portfolio_stock_lookup ON portfolios(stock_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_last_updated ON portfolios(updated_at DESC);

-- Dividends table indexes for date range queries
CREATE INDEX IF NOT EXISTS idx_dividend_payment_range ON dividends(payment_date DESC);
CREATE INDEX IF NOT EXISTS idx_dividend_ex_date_range ON dividends(ex_dividend_date DESC);
CREATE INDEX IF NOT EXISTS idx_dividend_stock_payment ON dividends(stock_id, payment_date DESC);
CREATE INDEX IF NOT EXISTS idx_dividend_year_month ON dividends(payment_date DESC, stock_id);

-- Disclosures table indexes for complex filtering
CREATE INDEX IF NOT EXISTS idx_disclosure_stock_received ON disclosures(stock_id, received_date DESC);
CREATE INDEX IF NOT EXISTS idx_disclosure_important_date ON disclosures(is_important, received_date DESC);
CREATE INDEX IF NOT EXISTS idx_disclosure_read_date ON disclosures(is_read, received_date DESC);
CREATE INDEX IF NOT EXISTS idx_disclosure_report_unique ON disclosures(report_number);
CREATE INDEX IF NOT EXISTS idx_disclosure_portfolio_filter ON disclosures(stock_id, is_important, is_read, received_date DESC);
