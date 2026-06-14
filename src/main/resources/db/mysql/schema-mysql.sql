-- =============================================================================
-- schema-mysql.sql
-- =============================================================================
-- Purpose : Full DDL for the TradingJournal application targeting MySQL 8.0.
--           This file is the SINGLE SOURCE OF TRUTH for the database schema when
--           running under the `mysql` Spring profile. Flyway is DISABLED in that
--           profile (spring.flyway.enabled=false), so the cross-DB-incompatible
--           Flyway migrations (V1..V7) are NOT applied on MySQL. Instead this
--           script creates the complete schema once.
--
-- Target  : MySQL 8.0+ (InnoDB, utf8mb4 / utf8mb4_unicode_ci).
--
-- How to apply (any ONE of the following):
--   1) Container init (recommended for local dev):
--        Mounted read-only into /docker-entrypoint-initdb.d/ by docker-compose,
--        so MySQL runs it automatically on first container start.
--   2) Manual:
--        mysql -u tradingjournal -p tradingjournal < schema-mysql.sql
--   3) Fresh DB:
--        CREATE DATABASE tradingjournal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--        then run this file against it.
--
-- Verification:
--   Boot the app with `--spring.profiles.active=mysql` and JPA_DDL_AUTO=validate.
--   Hibernate (org.hibernate.dialect.MySQLDialect) validates every @Entity column
--   against this schema. If anything drifts, startup fails loudly (no silent
--   corruption). This DDL was reconciled against Hibernate 6 schema-generation
--   output for MySQLDialect to guarantee `validate` passes.
--
-- Notes:
--   * Booleans map to MySQL BIT (Hibernate 6 MySQLDialect default), NOT TINYINT(1).
--   * @Enumerated(STRING) columns map to native MySQL ENUM(...) with values listed
--     ALPHABETICALLY (Hibernate 6 MySQLDialect default). This is what `validate`
--     expects, so do NOT change these to VARCHAR.
--   * LocalDateTime -> DATETIME(6); LocalDate -> DATE.
--   * Several numeric-looking columns are TEXT because they store encrypted
--     ciphertext via @Convert (Account.name, Portfolio.*, Transaction price/qty/...).
--   * stress_scenario.sector_impacts uses JSON (the entity declares
--     columnDefinition="jsonb" which is Postgres-only; on MySQL the @JdbcTypeCode
--     SqlTypes.JSON maps to native JSON, which is what validate checks).
--   * stress_test_result has NO JPA entity (native table from Flyway V4); it is
--     created here manually and translated to MySQL 8.0 syntax.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Base / referenced tables (no outgoing FK dependencies)
-- -----------------------------------------------------------------------------

create table users (
    enabled bit not null,
    password_change_required bit not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    last_login_at datetime(6),
    updated_at datetime(6),
    password varchar(255) not null,
    role varchar(255) not null,
    username varchar(255) not null,
    primary key (id),
    constraint UKr43af9ap4edm43mmtq01oddj6 unique (username)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table stocks (
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    industry varchar(100),
    exchange varchar(255),
    name varchar(255) not null,
    symbol varchar(255) not null,
    sector enum ('COMMUNICATION','CONSUMER_DISC','CONSUMER_STAP','ENERGY','FINANCE','HEALTH','INDUSTRIAL','MATERIALS','OTHER','REAL_ESTATE','TECH','UTILITIES'),
    primary key (id),
    constraint UK2oaank9l88k0ipjw48ua6fcty unique (symbol)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table accounts (
    is_default bit not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint,
    description varchar(500),
    name TEXT not null,
    account_type enum ('CUSTOM','GENERAL','ISA','PENSION') not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Tables referencing users / stocks / accounts
-- -----------------------------------------------------------------------------

create table account_risk_settings (
    account_capital decimal(19,4),
    concentration_alert_enabled bit,
    daily_loss_alert_enabled bit,
    kelly_fraction decimal(3,2),
    max_daily_loss_percent decimal(5,2),
    max_holding_days integer,
    max_open_positions integer,
    max_position_size_percent decimal(5,2),
    max_risk_per_trade_percent decimal(5,2),
    max_sector_concentration_percent decimal(5,2),
    max_stock_concentration_percent decimal(5,2),
    max_weekly_loss_percent decimal(5,2),
    account_id bigint,
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    primary key (id),
    constraint UKdwbrgby6wq6amff1cogel16ym unique (account_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table dividends (
    dividend_per_share decimal(10,2) not null,
    ex_dividend_date date not null,
    net_amount decimal(10,2) not null,
    payment_date date not null,
    quantity decimal(10,2) not null,
    tax_amount decimal(10,2),
    total_amount decimal(10,2) not null,
    account_id bigint,
    created_at datetime(6),
    id bigint not null auto_increment,
    stock_id bigint not null,
    updated_at datetime(6),
    memo varchar(500),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table portfolios (
    account_id bigint,
    created_at datetime(6),
    id bigint not null auto_increment,
    stock_id bigint not null,
    updated_at datetime(6),
    average_price TEXT not null,
    quantity TEXT not null,
    total_investment TEXT not null,
    primary key (id),
    constraint uk_portfolio_account_stock unique (account_id, stock_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table target_allocations (
    drift_threshold_percent decimal(5,2),
    is_active bit not null,
    priority integer,
    target_percent decimal(5,2) not null,
    account_id bigint not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    stock_id bigint not null,
    updated_at datetime(6),
    notes varchar(500),
    primary key (id),
    constraint uk_target_allocation_account_stock unique (account_id, stock_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table trade_plans (
    actual_entry_price decimal(19,4),
    actual_quantity decimal(19,4),
    actualrmultiple decimal(10,4),
    followed_plan bit,
    planned_entry_price decimal(19,4) not null,
    planned_position_value decimal(19,4),
    planned_quantity decimal(19,4) not null,
    planned_risk_amount decimal(19,4),
    planned_risk_percent decimal(10,4),
    planned_risk_reward_ratio decimal(10,4),
    planned_stop_loss_price decimal(19,4) not null,
    planned_take_profit2price decimal(19,4),
    planned_take_profit_price decimal(19,4),
    realized_pnl decimal(19,4),
    account_id bigint,
    created_at datetime(6),
    executed_at datetime(6),
    executed_transaction_id bigint,
    id bigint not null auto_increment,
    result_transaction_id bigint,
    stock_id bigint not null,
    updated_at datetime(6),
    valid_until datetime(6),
    version bigint,
    title varchar(200),
    market_context varchar(500),
    checklist TEXT,
    entry_conditions TEXT,
    execution_notes TEXT,
    exit_conditions TEXT,
    invalidation_conditions TEXT,
    notes TEXT,
    plan_type enum ('LONG','SHORT') not null,
    status enum ('CANCELLED','EXECUTED','EXPIRED','PLANNED') not null,
    strategy enum ('BREAKOUT','DAY_TRADE','DIVIDEND','GROWTH','MEAN_REVERSION','MOMENTUM','OTHER','SCALPING','SECTOR_ROTATION','SWING','VALUE'),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table transactions (
    initial_risk_amount decimal(19,4),
    r_multiple decimal(10,4),
    realized_pnl decimal(19,4),
    remaining_quantity decimal(19,4),
    risk_reward_ratio decimal(10,4),
    stop_loss_price decimal(19,4),
    take_profit_price decimal(19,4),
    account_id bigint,
    created_at datetime(6),
    id bigint not null auto_increment,
    stock_id bigint not null,
    transaction_date datetime(6) not null,
    updated_at datetime(6),
    commission TEXT,
    cost_basis TEXT,
    notes varchar(255),
    price TEXT not null,
    quantity TEXT not null,
    type enum ('BUY','SELL') not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table disclosures (
    is_important bit,
    is_read bit,
    created_at datetime(6),
    id bigint not null auto_increment,
    received_date datetime(6) not null,
    stock_id bigint not null,
    updated_at datetime(6),
    report_name varchar(500) not null,
    view_url varchar(1000),
    corp_code varchar(255) not null,
    corp_name varchar(255) not null,
    report_number varchar(255) not null,
    report_type varchar(255),
    submitter varchar(255) not null,
    summary TEXT,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table saved_screen (
    is_public bit not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    name varchar(100) not null,
    description varchar(500),
    criteria_json TEXT not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. trade_reviews depends on transactions
-- -----------------------------------------------------------------------------

create table trade_reviews (
    followed_plan bit,
    rating_score integer,
    created_at datetime(6),
    id bigint not null auto_increment,
    reviewed_at datetime(6),
    transaction_id bigint,
    updated_at datetime(6),
    screenshot_path varchar(500),
    tags varchar(500),
    entry_reason TEXT,
    exit_reason TEXT,
    lessons_learned TEXT,
    review_note TEXT,
    emotion_after enum ('ANXIOUS','CALM','CONFIDENT','EXCITED','FEARFUL','FRUSTRATED','GREEDY','NEUTRAL'),
    emotion_before enum ('ANXIOUS','CALM','CONFIDENT','EXCITED','FEARFUL','FRUSTRATED','GREEDY','NEUTRAL'),
    strategy enum ('BREAKOUT','DAY_TRADE','DIVIDEND','GROWTH','MEAN_REVERSION','MOMENTUM','OTHER','SCALPING','SECTOR_ROTATION','SWING','VALUE'),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 4. backtest_results -> backtest_trades
-- -----------------------------------------------------------------------------

create table backtest_results (
    avg_holding_days decimal(10,2),
    avg_loss decimal(15,2),
    avg_win decimal(15,2),
    cagr decimal(10,4),
    end_date date not null,
    final_capital decimal(15,2),
    initial_capital decimal(15,2),
    losing_trades integer,
    max_drawdown decimal(10,4),
    max_loss_streak integer,
    max_win_streak integer,
    profit_factor decimal(10,4),
    sharpe_ratio decimal(10,4),
    sortino_ratio decimal(10,4),
    start_date date not null,
    total_return decimal(10,4),
    total_trades integer,
    win_rate decimal(10,4),
    winning_trades integer,
    executed_at datetime(6),
    execution_time_ms bigint,
    id bigint not null auto_increment,
    strategy_type varchar(50),
    symbol varchar(50),
    strategy_name varchar(100) not null,
    benchmark_curve_json TEXT,
    drawdown_curve_json TEXT,
    equity_curve_json TEXT,
    equity_labels_json TEXT,
    monthly_performance_json TEXT,
    normalized_equity_curve_json TEXT,
    strategy_config TEXT,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table backtest_trades (
    entry_date date not null,
    entry_price decimal(15,2),
    exit_date date,
    exit_price decimal(15,2),
    holding_days integer,
    portfolio_value_at_entry decimal(15,2),
    portfolio_value_at_exit decimal(15,2),
    profit decimal(15,2),
    profit_percent decimal(10,4),
    quantity decimal(15,4),
    trade_number integer,
    backtest_result_id bigint not null,
    id bigint not null auto_increment,
    symbol varchar(50) not null,
    entry_signal varchar(100),
    exit_signal varchar(100),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 5. dashboard_configs -> dashboard_widgets
-- -----------------------------------------------------------------------------

create table dashboard_configs (
    active bit not null,
    compact_mode bit not null,
    grid_columns integer not null,
    refresh_interval integer not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    config_name varchar(255) not null,
    theme varchar(255),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table dashboard_widgets (
    display_order integer not null,
    gridx integer not null,
    gridy integer not null,
    height integer not null,
    visible bit not null,
    width integer not null,
    dashboard_config_id bigint,
    id bigint not null auto_increment,
    settings TEXT,
    title varchar(255),
    widget_key varchar(255) not null,
    widget_type enum ('ACTIVE_ALERTS','ALLOCATION_PIE','DRAWDOWN_CHART','EQUITY_CURVE','GOALS_PROGRESS','HOLDINGS_COUNT','HOLDINGS_LIST','MONTE_CARLO_CHART','MONTE_CARLO_SUMMARY','MONTHLY_RETURNS','PORTFOLIO_SUMMARY','PROFIT_LOSS_CARD','RECENT_TRANSACTIONS','RISK_METRICS','SCREENER_WATCHLIST','SECTOR_ALLOCATION','STREAK_INDICATOR','STRESS_TEST_SCENARIOS','STRESS_TEST_SUMMARY','TAX_HARVESTING_OPPORTUNITIES','TODAY_PERFORMANCE','TOP_PERFORMERS','TRADING_STATS','WORST_PERFORMERS') not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 6. Independent tables (no FK to other entity tables)
-- -----------------------------------------------------------------------------

create table alerts (
    related_value decimal(38,2),
    threshold_value decimal(38,2),
    account_id bigint,
    created_at datetime(6),
    expires_at datetime(6),
    id bigint not null auto_increment,
    read_at datetime(6),
    related_entity_id bigint,
    action_url varchar(255),
    message TEXT,
    related_entity_type varchar(255),
    title varchar(255) not null,
    alert_type enum ('CUSTOM','DAILY_LOSS_LIMIT','DRAWDOWN_WARNING','GOAL_COMPLETED','GOAL_DEADLINE','GOAL_MILESTONE','GOAL_OVERDUE','LOSING_STREAK','LOSS_LIMIT','PORTFOLIO_CHANGE','POSITION_SIZE','PROFIT_TARGET','SECTOR_CONCENTRATION','SYSTEM_INFO','SYSTEM_WARNING','TRADE_EXECUTED','WINNING_STREAK') not null,
    priority enum ('CRITICAL','HIGH','LOW','MEDIUM') not null,
    status enum ('ARCHIVED','DISMISSED','READ','UNREAD') not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table benchmark_prices (
    close_price decimal(15,2) not null,
    daily_return decimal(10,4),
    high_price decimal(15,2),
    low_price decimal(15,2),
    open_price decimal(15,2),
    price_date date not null,
    id bigint not null auto_increment,
    volume bigint,
    benchmark enum ('DOW','KOSDAQ','KOSPI','NASDAQ','SP500') not null,
    primary key (id),
    constraint UKdwpdct3xbvojtbcgsh74hhpcf unique (benchmark, price_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table economic_events (
    alert_enabled bit,
    eps_actual float(53),
    eps_estimate float(53),
    revenue_actual float(53),
    revenue_estimate float(53),
    created_at datetime(6),
    event_time datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    country varchar(10) not null,
    currency varchar(10),
    symbol varchar(20),
    source varchar(50),
    actual varchar(255),
    event_name varchar(255) not null,
    external_id varchar(255),
    forecast varchar(255),
    notes TEXT,
    previous varchar(255),
    unit varchar(255),
    event_type enum ('CENTRAL_BANK','DIVIDEND','EARNINGS','ECONOMIC_INDICATOR','HOLIDAY','IPO','OTHER') not null,
    importance enum ('HIGH','LOW','MEDIUM') not null,
    primary key (id),
    constraint uk_event_unique unique (event_time, event_name, country, symbol)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table goals (
    current_value decimal(19,4),
    deadline date,
    last_milestone integer,
    milestone_interval integer,
    notification_enabled bit,
    progress_percent decimal(10,2),
    start_date date not null,
    start_value decimal(19,4),
    target_value decimal(19,4) not null,
    account_id bigint,
    completed_at datetime(6),
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    version bigint,
    description varchar(1000),
    notes varchar(2000),
    name varchar(255) not null,
    goal_type enum ('CUSTOM','DIVIDEND_INCOME','MAX_DRAWDOWN_LIMIT','RETURN_RATE','SAVINGS_AMOUNT','SHARPE_RATIO','TARGET_AMOUNT','TRADE_COUNT','WIN_RATE') not null,
    status enum ('ACTIVE','CANCELLED','COMPLETED','FAILED','PAUSED') not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table historical_prices (
    adj_close decimal(15,4),
    close_price decimal(15,4) not null,
    high_price decimal(15,4),
    low_price decimal(15,4),
    open_price decimal(15,4),
    price_date date not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    volume bigint,
    symbol varchar(20) not null,
    primary key (id),
    constraint UK2gt2lpndinaud5f5m78w8o1g9 unique (symbol, price_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table price_alert (
    current_price decimal(15,2),
    is_active bit not null,
    is_triggered bit not null,
    notification_sent bit not null,
    threshold_price decimal(15,2),
    created_at datetime(6),
    id bigint not null auto_increment,
    stock_id bigint not null,
    triggered_at datetime(6),
    updated_at datetime(6),
    user_id bigint not null,
    symbol varchar(20) not null,
    alert_type enum ('PERCENT_CHANGE','PRICE_ABOVE','PRICE_BELOW','VOLUME_SPIKE') not null,
    `condition` enum ('EQUALS','GREATER_THAN','LESS_THAN','PERCENT_DOWN','PERCENT_UP') not null,
    primary key (id),
    -- V5 CHECK constraints (MySQL 8.0 enforces CHECK). Redundant with the ENUM
    -- definitions above but kept per spec for explicit business-rule documentation.
    constraint chk_price_alert_type
        check (alert_type in ('PRICE_ABOVE','PRICE_BELOW','PERCENT_CHANGE','VOLUME_SPIKE')),
    constraint chk_price_alert_condition
        check (`condition` in ('GREATER_THAN','LESS_THAN','EQUALS','PERCENT_UP','PERCENT_DOWN'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table stock_fundamentals (
    average_volume decimal(15,0),
    beta decimal(10,4),
    book_value_growth decimal(10,4),
    book_value_per_share decimal(15,2),
    current_price decimal(15,2),
    current_ratio decimal(10,4),
    debt_to_equity decimal(10,4),
    dividend_per_share decimal(10,2),
    dividend_yield decimal(10,4),
    earnings_per_share decimal(15,2),
    eps_growth decimal(10,4),
    ev_to_ebitda decimal(10,2),
    fifty_two_week_high decimal(15,2),
    fifty_two_week_low decimal(15,2),
    gross_margin decimal(10,4),
    last_updated date,
    market_cap decimal(15,2),
    operating_margin decimal(10,4),
    payout_ratio decimal(10,4),
    pb_ratio decimal(10,2),
    pe_ratio decimal(10,2),
    peg_ratio decimal(10,2),
    profit_margin decimal(10,4),
    ps_ratio decimal(10,2),
    quick_ratio decimal(10,4),
    return_on_assets decimal(10,4),
    return_on_equity decimal(10,4),
    revenue_growth decimal(10,4),
    revenue_per_share decimal(15,2),
    total_assets decimal(15,2),
    total_cash decimal(15,2),
    total_debt decimal(15,2),
    total_revenue decimal(15,2),
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    fiscal_year_end varchar(20),
    symbol varchar(20) not null,
    industry varchar(100),
    company_name varchar(200),
    sector enum ('COMMUNICATION','CONSUMER_DISC','CONSUMER_STAP','ENERGY','FINANCE','HEALTH','INDUSTRIAL','MATERIALS','OTHER','REAL_ESTATE','TECH','UTILITIES'),
    primary key (id),
    constraint UKiw3t32thccm0262tlohdf6821 unique (symbol)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table strategy_templates (
    commission_rate decimal(5,4),
    is_default bit,
    position_size_percent decimal(5,2),
    stop_loss_percent decimal(5,2),
    take_profit_percent decimal(5,2),
    usage_count integer,
    account_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    color varchar(20),
    strategy_type varchar(50) not null,
    name varchar(100) not null,
    description TEXT,
    parameters_json TEXT,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table trading_journals (
    discipline_score integer,
    focus_score integer,
    journal_date date not null,
    trade_summary_count integer,
    trade_summary_profit decimal(38,2),
    trade_summary_win_rate decimal(38,2),
    account_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    tags varchar(500),
    execution_review TEXT,
    lessons_learned TEXT,
    market_overview TEXT,
    tomorrow_plan TEXT,
    trading_plan TEXT,
    evening_emotion enum ('ANXIOUS','CALM','CONFIDENT','EXCITED','FEARFUL','FRUSTRATED','GREEDY','NEUTRAL'),
    morning_emotion enum ('ANXIOUS','CALM','CONFIDENT','EXCITED','FEARFUL','FRUSTRATED','GREEDY','NEUTRAL'),
    primary key (id),
    constraint UK77hfi0q0swkndbbisxaxql7h3 unique (account_id, journal_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 7. stress_scenario (entity) -> stress_test_result (NON-ENTITY, from Flyway V4)
-- -----------------------------------------------------------------------------

create table stress_scenario (
    end_date date,
    is_predefined bit not null,
    market_shock_percent decimal(10,2) not null,
    start_date date,
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    scenario_code varchar(50) not null,
    name varchar(200) not null,
    description varchar(1000),
    sector_impacts json,
    primary key (id),
    constraint idx_stress_scenario_code unique (scenario_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- NON-ENTITY table: created only by Flyway V4 (no JPA entity). Translated to
-- MySQL 8.0: BIGINT AUTO_INCREMENT instead of GENERATED ALWAYS AS IDENTITY,
-- JSON instead of JSON (already valid), TIMESTAMP DEFAULT CURRENT_TIMESTAMP kept.
create table stress_test_result (
    id bigint not null auto_increment,
    account_id bigint,
    scenario_id bigint,
    portfolio_value_before decimal(15,2),
    portfolio_value_after decimal(15,2),
    total_impact_percent decimal(10,4),
    position_impacts json,
    executed_at timestamp default current_timestamp,
    primary key (id),
    constraint fk_stress_result_scenario foreign key (scenario_id) references stress_scenario (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- =============================================================================
-- Foreign keys (entity @ManyToOne / @JoinColumn relationships only).
-- Plain Long reference columns (accounts.user_id, alerts.account_id,
-- dashboard_configs.user_id, goals.account_id, price_alert.user_id/stock_id,
-- strategy_templates.account_id, trade_plans.executed/result_transaction_id,
-- trading_journals.account_id) are NOT real FKs and get indexes only.
-- =============================================================================

alter table account_risk_settings
    add constraint FKmayornle53d32p44688kb53c8 foreign key (account_id) references accounts (id);
alter table backtest_trades
    add constraint FKsvudyy6efrxjaviuewxho8g2k foreign key (backtest_result_id) references backtest_results (id);
alter table dashboard_widgets
    add constraint FKdmkujev613j4dhyr65607s9fg foreign key (dashboard_config_id) references dashboard_configs (id);
alter table disclosures
    add constraint FK8kd0lrxwf5fcnu3pe73s6sa5k foreign key (stock_id) references stocks (id);
alter table dividends
    add constraint FKgocryygked2ygls5g1349ef6i foreign key (account_id) references accounts (id);
alter table dividends
    add constraint FK67vsjoticwf7fmircovn0wb21 foreign key (stock_id) references stocks (id);
alter table portfolios
    add constraint FKeedw14haxsj5du1ca0sh2ryee foreign key (account_id) references accounts (id);
alter table portfolios
    add constraint FKtbnfhhvrpflvutbd4ystqow6c foreign key (stock_id) references stocks (id);
alter table saved_screen
    add constraint FKkix9lheljk3ppvrxegbjwq3w9 foreign key (user_id) references users (id);
alter table target_allocations
    add constraint FKc9n8wxyaq3hdxfh97gqqi0y1s foreign key (account_id) references accounts (id);
alter table target_allocations
    add constraint FK969agfn3oirn6a8eqjfoy5l5p foreign key (stock_id) references stocks (id);
alter table trade_plans
    add constraint FKs1am72uryx5mpa51ujcqwk6gb foreign key (account_id) references accounts (id);
alter table trade_plans
    add constraint FK7qid2gnx0nki8aq5q2srcaxdu foreign key (stock_id) references stocks (id);
alter table trade_reviews
    add constraint FKeej316b673a3ir7l0r3xefb6d foreign key (transaction_id) references transactions (id);
alter table transactions
    add constraint FK20w7wsg13u9srbq3bd7chfxdh foreign key (account_id) references accounts (id);
alter table transactions
    add constraint FKru6v24w1h01nvqoox4l2wkybv foreign key (stock_id) references stocks (id);

-- =============================================================================
-- Indexes (entity @Table indexes + @Column unique + V1 performance indexes +
-- V3..V6 indexes). Index names are kept unique across the whole schema.
-- Extra indexes beyond what Hibernate emits are harmless for `validate`.
-- =============================================================================

-- account_risk_settings
create index idx_risk_settings_account on account_risk_settings (account_id);

-- accounts
create index idx_account_name on accounts (name(255));
create index idx_account_type on accounts (account_type);
create index idx_account_is_default on accounts (is_default);
create index idx_account_user_id on accounts (user_id);
-- V6 composite
create index idx_account_user_default on accounts (user_id, is_default);

-- alerts
create index idx_alert_status on alerts (status);
create index idx_alert_type on alerts (alert_type);
create index idx_alert_created on alerts (created_at);
create index idx_alert_account on alerts (account_id);

-- dashboard_configs
create index idx_dashboard_user on dashboard_configs (user_id);

-- disclosures
create index idx_disclosure_stock_id on disclosures (stock_id);
create index idx_disclosure_received_date on disclosures (received_date);
create index idx_disclosure_is_important on disclosures (is_important);
create index idx_disclosure_is_read on disclosures (is_read);
create index idx_disclosure_report_number on disclosures (report_number);
create index idx_stock_received_date on disclosures (stock_id, received_date);
create index idx_important_received_date on disclosures (is_important, received_date);
create index idx_read_received_date on disclosures (is_read, received_date);
-- V1 performance indexes
create index idx_disclosure_stock_received on disclosures (stock_id, received_date desc);
create index idx_disclosure_important_date on disclosures (is_important, received_date desc);
create index idx_disclosure_read_date on disclosures (is_read, received_date desc);
create index idx_disclosure_portfolio_filter on disclosures (stock_id, is_important, is_read, received_date desc);

-- dividends
create index idx_dividend_payment_date on dividends (payment_date);
create index idx_dividend_stock_id on dividends (stock_id);
create index idx_dividend_ex_date on dividends (ex_dividend_date);
create index idx_dividend_account_id on dividends (account_id);
create index idx_dividend_account_stock on dividends (account_id, stock_id);
-- V1 performance indexes
create index idx_dividend_payment_range on dividends (payment_date desc);
create index idx_dividend_ex_date_range on dividends (ex_dividend_date desc);
create index idx_dividend_stock_payment on dividends (stock_id, payment_date desc);
create index idx_dividend_year_month on dividends (payment_date desc, stock_id);

-- economic_events
create index idx_event_time on economic_events (event_time);
create index idx_event_type on economic_events (event_type);
create index idx_event_country on economic_events (country);
create index idx_event_symbol on economic_events (symbol);
create index idx_event_importance on economic_events (importance);

-- goals
create index idx_goal_type on goals (goal_type);
create index idx_goal_status on goals (status);
create index idx_goal_deadline on goals (deadline);

-- historical_prices
create index idx_historical_symbol on historical_prices (symbol);
create index idx_historical_date on historical_prices (price_date);
create index idx_historical_symbol_date on historical_prices (symbol, price_date);

-- portfolios
create index idx_portfolio_stock_id on portfolios (stock_id);
create index idx_portfolio_account_id on portfolios (account_id);
create index idx_portfolio_account_stock on portfolios (account_id, stock_id);
create index idx_portfolio_updated_at on portfolios (updated_at);
-- V1 performance indexes
create index idx_portfolio_last_updated on portfolios (updated_at desc);

-- price_alert
create index idx_price_alert_user on price_alert (user_id);
create index idx_price_alert_symbol on price_alert (symbol);
-- V5 had a partial index "WHERE is_active = TRUE"; partial indexes are not
-- supported on MySQL, so the WHERE clause is dropped (plain index).
create index idx_price_alert_active on price_alert (is_active);
create index idx_price_alert_triggered on price_alert (is_triggered);

-- saved_screen
create index idx_saved_screen_user_id on saved_screen (user_id);
create index idx_saved_screen_name on saved_screen (name);

-- stock_fundamentals
create index idx_fundamentals_symbol on stock_fundamentals (symbol);
create index idx_fundamentals_sector on stock_fundamentals (sector);
create index idx_fundamentals_pe_ratio on stock_fundamentals (pe_ratio);
create index idx_fundamentals_market_cap on stock_fundamentals (market_cap);

-- stocks (symbol is already covered by the UNIQUE constraint; V1's *_perf/
-- *_search/*_filter duplicates dropped to avoid MySQL "duplicate index" warnings)
create index idx_stock_symbol on stocks (symbol);
create index idx_stock_name on stocks (name);
create index idx_stock_exchange on stocks (exchange);

-- strategy_templates
create index idx_template_account on strategy_templates (account_id);
create index idx_template_strategy on strategy_templates (strategy_type);

-- stress_scenario
create index idx_stress_scenario_predefined on stress_scenario (is_predefined);

-- stress_test_result (V4 indexes)
create index idx_stress_result_account on stress_test_result (account_id);
create index idx_stress_result_scenario on stress_test_result (scenario_id);

-- target_allocations
create index idx_target_allocation_account_id on target_allocations (account_id);
create index idx_target_allocation_stock_id on target_allocations (stock_id);
create index idx_target_allocation_is_active on target_allocations (is_active);

-- trade_plans
create index idx_trade_plan_status on trade_plans (status);
create index idx_trade_plan_account on trade_plans (account_id);
create index idx_trade_plan_stock on trade_plans (stock_id);
create index idx_trade_plan_valid_until on trade_plans (valid_until);
create index idx_trade_plan_created on trade_plans (created_at);

-- trading_journals
create index idx_journal_date on trading_journals (journal_date);
create index idx_journal_account on trading_journals (account_id);

-- transactions
create index idx_transaction_date on transactions (transaction_date);
create index idx_stock_date on transactions (stock_id, transaction_date);
create index idx_transaction_account_stock on transactions (account_id, stock_id);
create index idx_transaction_account_date on transactions (account_id, transaction_date);
create index idx_transaction_fifo on transactions (account_id, stock_id, type, transaction_date, remaining_quantity);
-- V1 performance indexes
create index idx_transaction_stock_date_perf on transactions (stock_id, transaction_date desc);
create index idx_transaction_type_date_perf on transactions (type, transaction_date desc);
create index idx_transaction_date_range on transactions (transaction_date desc);
create index idx_transaction_audit_created on transactions (created_at);
create index idx_transaction_audit_updated on transactions (updated_at);

-- users
create index idx_user_username on users (username);

-- =============================================================================
-- Seed data: predefined historical stress scenarios (Flyway V4).
-- market_shock_percent uses DECIMAL(10,2) to match the StressScenario entity.
-- =============================================================================

insert into stress_scenario
    (scenario_code, name, description, start_date, end_date, market_shock_percent, is_predefined)
values
    ('GFC_2008',          'Global Financial Crisis 2008', 'The 2008 financial crisis caused by subprime mortgage collapse', '2008-09-01', '2009-03-09', -56.80, true),
    ('COVID_2020',        'COVID-19 Crash',               'Market crash due to COVID-19 pandemic',                          '2020-02-19', '2020-03-23', -33.90, true),
    ('DOT_COM_2000',      'Dot-com Bubble Burst',         'Technology stock crash after the dot-com bubble',               '2000-03-10', '2002-10-09', -78.00, true),
    ('BLACK_MONDAY_1987', 'Black Monday 1987',            'Single largest one-day market crash',                           '1987-10-19', '1987-10-19', -22.60, true),
    ('ASIAN_CRISIS_1997', 'Asian Financial Crisis',       'Financial crisis affecting East Asian economies',               '1997-07-02', '1998-01-12', -60.00, true);
