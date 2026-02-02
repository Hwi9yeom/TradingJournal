-- Stress Scenario definitions
CREATE TABLE IF NOT EXISTS stress_scenario (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    market_shock_percent DECIMAL(10,4) NOT NULL,
    sector_impacts JSON,
    is_predefined BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Predefined historical scenarios
INSERT INTO stress_scenario (scenario_code, name, description, start_date, end_date, market_shock_percent, is_predefined) VALUES
('GFC_2008', 'Global Financial Crisis 2008', 'The 2008 financial crisis caused by subprime mortgage collapse', '2008-09-01', '2009-03-09', -56.80, TRUE),
('COVID_2020', 'COVID-19 Crash', 'Market crash due to COVID-19 pandemic', '2020-02-19', '2020-03-23', -33.90, TRUE),
('DOT_COM_2000', 'Dot-com Bubble Burst', 'Technology stock crash after the dot-com bubble', '2000-03-10', '2002-10-09', -78.00, TRUE),
('BLACK_MONDAY_1987', 'Black Monday 1987', 'Single largest one-day market crash', '1987-10-19', '1987-10-19', -22.60, TRUE),
('ASIAN_CRISIS_1997', 'Asian Financial Crisis', 'Financial crisis affecting East Asian economies', '1997-07-02', '1998-01-12', -60.00, TRUE);

-- Stress Test Results
CREATE TABLE IF NOT EXISTS stress_test_result (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT,
    scenario_id BIGINT REFERENCES stress_scenario(id),
    portfolio_value_before DECIMAL(15,2),
    portfolio_value_after DECIMAL(15,2),
    total_impact_percent DECIMAL(10,4),
    position_impacts JSON,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stress_result_account ON stress_test_result(account_id);
CREATE INDEX idx_stress_result_scenario ON stress_test_result(scenario_id);
