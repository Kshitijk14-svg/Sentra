ALTER TABLE checks
    ADD COLUMN state                 TEXT NOT NULL DEFAULT 'PENDING',
    ADD COLUMN consecutive_failures  INT  NOT NULL DEFAULT 0,
    ADD COLUMN consecutive_successes INT  NOT NULL DEFAULT 0,
    ADD COLUMN failure_threshold     INT  NOT NULL DEFAULT 3,
    ADD COLUMN recovery_threshold    INT  NOT NULL DEFAULT 2;

CREATE TABLE incidents (
    id         BIGSERIAL PRIMARY KEY,
    check_id   BIGINT NOT NULL REFERENCES checks(id) ON DELETE CASCADE,
    opened_at  TIMESTAMPTZ NOT NULL,
    closed_at  TIMESTAMPTZ,
    last_error TEXT
);

-- At most one open incident per check, enforced by the database.
CREATE UNIQUE INDEX uq_incident_open_per_check ON incidents (check_id) WHERE closed_at IS NULL;
CREATE INDEX idx_incidents_check_opened ON incidents (check_id, opened_at DESC);
