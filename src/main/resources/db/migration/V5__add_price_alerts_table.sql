-- Price Alert table for real-time notifications
CREATE TABLE IF NOT EXISTS price_alert (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT,
    stock_id BIGINT,
    symbol VARCHAR(20) NOT NULL,
    alert_type VARCHAR(30) NOT NULL,
    threshold_price DECIMAL(15,4) NOT NULL,
    current_price DECIMAL(15,4),
    condition VARCHAR(20) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_triggered BOOLEAN DEFAULT FALSE,
    triggered_at TIMESTAMP,
    notification_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_alert_type CHECK (alert_type IN ('PRICE_ABOVE', 'PRICE_BELOW', 'PERCENT_CHANGE', 'VOLUME_SPIKE')),
    CONSTRAINT chk_condition CHECK (condition IN ('GREATER_THAN', 'LESS_THAN', 'EQUALS', 'PERCENT_UP', 'PERCENT_DOWN'))
);

CREATE INDEX idx_price_alert_user ON price_alert(user_id);
CREATE INDEX idx_price_alert_symbol ON price_alert(symbol);
CREATE INDEX idx_price_alert_active ON price_alert(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_price_alert_triggered ON price_alert(is_triggered);
