-- CRON (heartbeat) checks: the monitored job pushes pings to /ping/{token}; Sentra alerts when they stop.
ALTER TABLE checks
    ADD COLUMN ping_token       TEXT,           -- secret in the public ping URL (CRON checks only)
    ADD COLUMN last_ping_at     TIMESTAMPTZ,
    ADD COLUMN last_ping_status TEXT,           -- SUCCESS or FAIL
    ADD COLUMN last_start_at    TIMESTAMPTZ;    -- set by /start so the UI can show a job as running

CREATE UNIQUE INDEX uq_checks_ping_token ON checks (ping_token) WHERE ping_token IS NOT NULL;
