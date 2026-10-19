-- Per-type settings and richer run results. Later stages add their own columns in their own migrations.
ALTER TABLE checks
    ADD COLUMN site   TEXT,                                  -- grouping key: one card per site in the UI
    ADD COLUMN config JSONB NOT NULL DEFAULT '{}'::jsonb;    -- type-specific settings (host, port, ...)

ALTER TABLE check_runs
    ADD COLUMN detail       TEXT,                            -- human-readable extra facts about the run
    ADD COLUMN inconclusive BOOLEAN NOT NULL DEFAULT FALSE;  -- could not tell: neither success nor failure

-- Existing HTTP checks: group by the hostname of their URL.
UPDATE checks SET site = substring(target from '^https?://([^/:?#]+)') WHERE site IS NULL;

CREATE INDEX idx_checks_project_site ON checks (project_id, site);
