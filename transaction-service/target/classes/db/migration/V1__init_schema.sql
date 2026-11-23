CREATE TABLE IF NOT EXISTS accounts (
    account_id      VARCHAR(64) PRIMARY KEY,
    holder_name     VARCHAR(120) NOT NULL,
    home_country    VARCHAR(3) NOT NULL DEFAULT 'IDN',
    home_city       VARCHAR(80) NOT NULL,
    daily_limit     NUMERIC(18, 2) NOT NULL,
    avg_tx_amount   NUMERIC(18, 2) NOT NULL DEFAULT 0,
    is_blacklisted  BOOLEAN NOT NULL DEFAULT FALSE,
    risk_tier       VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transactions (
    tx_id              UUID PRIMARY KEY,
    idempotency_key    VARCHAR(128) NOT NULL UNIQUE,
    source_account_id  VARCHAR(64) NOT NULL REFERENCES accounts(account_id),
    target_account_id  VARCHAR(64) REFERENCES accounts(account_id),
    amount             NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3) NOT NULL DEFAULT 'IDR',
    tx_type            VARCHAR(30) NOT NULL,
    merchant_name      VARCHAR(150),
    merchant_category  VARCHAR(64),
    country_code       VARCHAR(3) NOT NULL,
    city               VARCHAR(80) NOT NULL,
    latitude           DOUBLE PRECISION NOT NULL,
    longitude          DOUBLE PRECISION NOT NULL,
    device_id          VARCHAR(128),
    ip_address         VARCHAR(64),
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS risk_rules (
    rule_code        VARCHAR(64) PRIMARY KEY,
    rule_name        VARCHAR(120) NOT NULL,
    category         VARCHAR(40) NOT NULL,
    description      TEXT,
    weight           INT NOT NULL CHECK (weight BETWEEN 0 AND 100),
    threshold_value  DOUBLE PRECISION NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fraud_decisions (
    decision_id            UUID PRIMARY KEY,
    tx_id                  UUID NOT NULL UNIQUE REFERENCES transactions(tx_id),
    account_id             VARCHAR(64) NOT NULL REFERENCES accounts(account_id),
    risk_score             INT NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_level             VARCHAR(20) NOT NULL,
    decision_status        VARCHAR(20) NOT NULL,
    triggered_rules        TEXT[] NOT NULL DEFAULT '{}',
    evaluation_latency_ms  BIGINT NOT NULL,
    evaluated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Composite index untuk Window Functions (Z-score & LAG antar transaksi per akun)
CREATE INDEX idx_tx_source_created
    ON transactions (source_account_id, created_at DESC);

-- Composite index untuk Recursive CTE (pelacakan rantai transfer melingkar A -> B -> C -> A)
CREATE INDEX idx_tx_transfer_graph
    ON transactions (source_account_id, target_account_id, created_at DESC)
    WHERE target_account_id IS NOT NULL;

-- Partial index khusus investigasi transaksi berisiko tinggi (hemat ukuran & mempercepat query alert)
CREATE INDEX idx_decisions_high_risk_recent
    ON fraud_decisions (evaluated_at DESC, account_id)
    WHERE risk_level IN ('HIGH', 'CRITICAL');

-- GIN index untuk pencarian cepat berdasarkan kode rule di dalam array triggered_rules
CREATE INDEX idx_decisions_triggered_rules_gin
    ON fraud_decisions USING GIN (triggered_rules);