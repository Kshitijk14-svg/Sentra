-- Tenancy: every check now belongs to a project, which belongs to a user.
-- Checks created before auth existed have no owner; they were dev data only, so drop them
-- (cascades to check_runs, incidents, alert_rules and alert_deliveries).
DELETE FROM checks WHERE project_id IS NULL;

ALTER TABLE checks ALTER COLUMN project_id SET NOT NULL;
CREATE INDEX idx_checks_project ON checks (project_id);
