-- Market sentiment snapshots: one value per indicator per day.
-- Values are read off public dashboards (CNN Fear & Greed, NAAIM, AAII, CoinGlass,
-- MVRV Z-Score, Puell Multiple, RHODL band, ...) and normalised into fear/greed
-- zones by SentimentEvaluator using each provider's published thresholds.
--
-- Scope: H2 / PostgreSQL only (the `mysql` profile disables Flyway and owns its
-- schema in db/mysql/schema-mysql.sql).

CREATE TABLE IF NOT EXISTS market_sentiments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    indicator VARCHAR(30) NOT NULL,
    recorded_date DATE NOT NULL,
    -- "value"는 H2 예약어라 컴럼명을 indicator_value로 둠
    indicator_value DECIMAL(19,4) NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sentiment_indicator_date UNIQUE (indicator, recorded_date)
);

CREATE INDEX IF NOT EXISTS idx_sentiment_date ON market_sentiments(recorded_date);
CREATE INDEX IF NOT EXISTS idx_sentiment_indicator ON market_sentiments(indicator);
