-- New checks are due immediately; the scheduler advances next_run_at when it claims them.
ALTER TABLE checks ADD COLUMN next_run_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_checks_due ON checks (next_run_at) WHERE enabled;
