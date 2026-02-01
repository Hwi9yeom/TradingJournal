-- Add strategy_type column for efficient querying
-- This replaces LIKE searches on strategy_config JSON field

-- Add the new column
ALTER TABLE backtest_result ADD COLUMN IF NOT EXISTS strategy_type VARCHAR(50);

-- Create index for fast lookups
CREATE INDEX IF NOT EXISTS idx_backtest_strategy_type ON backtest_result(strategy_type);

-- Add normalized_equity_curve_json for pre-computed chart data
ALTER TABLE backtest_result ADD COLUMN IF NOT EXISTS normalized_equity_curve_json TEXT;

-- Note: Data migration for existing records should be done in application code
-- because JSON parsing in SQL is database-specific
