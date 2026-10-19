-- project_id is nullable until Phase 5 introduces auth and tenant scoping.
CREATE TABLE checks (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT REFERENCES project(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    type             TEXT NOT NULL DEFAULT 'HTTP',
    target           TEXT NOT NULL,
    expected_status  INT NOT NULL DEFAULT 200,
    timeout_ms       INT NOT NULL DEFAULT 10000,
    interval_seconds INT NOT NULL DEFAULT 60,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE check_runs (
    id          BIGSERIAL PRIMARY KEY,
    check_id    BIGINT NOT NULL REFERENCES checks(id) ON DELETE CASCADE,
    started_at  TIMESTAMPTZ NOT NULL,
    success     BOOLEAN NOT NULL,
    status_code INT,
    latency_ms  BIGINT NOT NULL,
    error       TEXT
);

CREATE INDEX idx_check_runs_check_started ON check_runs (check_id, started_at DESC);
