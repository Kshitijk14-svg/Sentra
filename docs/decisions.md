# Design decisions

- **Java 21 + Spring Boot 4.1, Maven**: mainstream, well-documented stack for the viva.
- **PostgreSQL 16 + Flyway, `ddl-auto: validate`**: schema changes are versioned migrations, never auto-generated.
- **JVM timezone forced to UTC** (`SentraApplication`): Postgres rejects the `Asia/Calcutta` zone id.
- **Webhook-only alerting**: no email provider, no paid keys.
- **Hysteresis for incidents**: N consecutive failures open an incident, M consecutive successes resolve it, to avoid flapping alerts.
- **SSRF guard**: resolve DNS first, then block private/loopback/link-local addresses.
- **Scheduler claims before probing**: `next_run_at` is advanced in a short transaction using `FOR UPDATE SKIP LOCKED`, then probes are dispatched; this prevents double runs across instances.
- **Bounded probe pool with `AbortPolicy`**: a full queue skips a run instead of blocking the scheduler thread (no `CallerRuns`).
- **Host Postgres port 55432**: avoids clashing with a native Postgres on 5432.
- **Incident state lives on `checks`** (state + consecutive counters, thresholds default 3/2): `EvaluationService` is pure; `IncidentService` applies it under a row lock (`PESSIMISTIC_WRITE`) so overlapping runs of one check can't corrupt counters. A partial unique index allows at most one open incident per check.
- **Alert dedup via insert-first**: the `alert_deliveries` row is inserted (`ON CONFLICT DO NOTHING`) *before* the webhook is sent; the `uq_alert_once` unique index decides who sends. A failed send is recorded as FAILED and not retried (at-most-once). One webhook rule per check (`uq_alert_rule_check_channel`).
- **Alerts are event-driven**: `IncidentService` publishes `IncidentOpened/ClosedEvent`; `AlertListener` handles them `AFTER_COMMIT` on a separate async pool so a slow webhook never holds a probe thread. A reminder sweep (15/30/60 min, then hourly) covers long-open incidents.
- **Tenancy = user -> project -> check**: register creates a default project; user-facing queries (`findByIdForUser`, `findAllForUser`, `findRecentForUser`) embed ownership and return 404 on a cross-tenant hit. Engine code (scheduler, `IncidentService`, `AlertService`) looks checks up by id with no user, deliberately; never call those from a controller.
- **Two filter chains**: `/api/**` needs a JWT (401 otherwise); the public chain permits `/error`, `/actuator/health`, `/ping/**`, `/webhook/**`, `/status/**` and denies the rest. `/error` must be permitted or every API 404/400 is rewritten to 403 (caught by `TenantIsolationTest`).
- **SSRF guard validates, it does not pin**: checked at creation and immediately before each probe/webhook, rejecting if ANY resolved address is internal. A short DNS-rebinding window between that lookup and the connect remains (accepted trade-off; true pinning would need Apache HttpClient 5 with a custom DnsResolver). `sentra.ssrf.allow-private` is for local demos only.
- **Tests need the local Postgres**: `TenantIsolationTest` runs against docker-compose Postgres on 55432 (no Testcontainers) and sets the JVM zone to UTC itself because it bypasses `main`.
