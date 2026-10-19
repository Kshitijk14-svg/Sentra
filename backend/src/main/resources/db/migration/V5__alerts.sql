CREATE TABLE alert_rules (
    id         BIGSERIAL PRIMARY KEY,
    check_id   BIGINT NOT NULL REFERENCES checks(id) ON DELETE CASCADE,
    channel    TEXT NOT NULL DEFAULT 'WEBHOOK',
    target_url TEXT NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- One rule per check per channel, so (incident, channel, transition) below identifies a delivery.
CREATE UNIQUE INDEX uq_alert_rule_check_channel ON alert_rules (check_id, channel);

-- One row per notification attempt. transition_type is OPENED, CLOSED or REMINDER_<n>.
-- The unique index, not application code, guarantees at-most-once delivery (Doc 1 §6.5):
-- the row is inserted before sending, and a duplicate insert means someone already sent it.
CREATE TABLE alert_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    incident_id     BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    channel         TEXT NOT NULL,
    transition_type TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING',
    error           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_alert_once ON alert_deliveries (incident_id, channel, transition_type);
