-- Stock Screener Database Migration
-- Adds stock fundamentals and saved screen tables for screener functionality

-- Stock Fundamentals table for screener
CREATE TABLE IF NOT EXISTS stock_fundamentals (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(20),
    company_name VARCHAR(255),

    -- Valuation metrics
    pe_ratio DECIMAL(10,2),
    forward_pe DECIMAL(10,2),
    pb_ratio DECIMAL(10,2),
    ps_ratio DECIMAL(10,2),
    peg_ratio DECIMAL(10,2),

    -- Profitability
    roe DECIMAL(10,4),
    roa DECIMAL(10,4),
    profit_margin DECIMAL(10,4),
    operating_margin DECIMAL(10,4),

    -- Dividends
    dividend_yield DECIMAL(10,4),
    dividend_payout_ratio DECIMAL(10,4),

    -- Growth
    revenue_growth DECIMAL(10,4),
    earnings_growth DECIMAL(10,4),

    -- Size
    market_cap DECIMAL(20,2),
    sector VARCHAR(100),
    industry VARCHAR(100),

    -- Debt
    debt_to_equity DECIMAL(10,4),
    current_ratio DECIMAL(10,4),

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_fundamentals_symbol_exchange UNIQUE(symbol, exchange)
);

-- Indexes for common screener queries
CREATE INDEX IF NOT EXISTS idx_fundamentals_pe ON stock_fundamentals(pe_ratio);
CREATE INDEX IF NOT EXISTS idx_fundamentals_pb ON stock_fundamentals(pb_ratio);
CREATE INDEX IF NOT EXISTS idx_fundamentals_dividend ON stock_fundamentals(dividend_yield);
CREATE INDEX IF NOT EXISTS idx_fundamentals_market_cap ON stock_fundamentals(market_cap);
CREATE INDEX IF NOT EXISTS idx_fundamentals_sector ON stock_fundamentals(sector);
CREATE INDEX IF NOT EXISTS idx_fundamentals_roe ON stock_fundamentals(roe);

-- Saved Screens table
CREATE TABLE IF NOT EXISTS saved_screen (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    criteria_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_screen_user ON saved_screen(user_id);
