# Sentra — Build Plan & Progress Tracker

Source docs: `Sentra - 1 - How It Works...pdf` (concepts/architecture/viva) and
`Sentra - 2 - Build Plan Phase-wise.pdf` (original 12-week plan). This file is the **compressed
2-week plan actually being followed**, plus a running checklist. Update the checkboxes as work
completes so any future session can resume from here without re-deriving context.

## Scope decisions (locked in, do not revisit)

- Timeline: 2 weeks, part-time (~3-4 hrs/day). Priority: working core system, demoable live.
- Building: source plan's Phases 0-5 only (skeleton → one check → engine → incidents → alerting
  → auth/SSRF). Cut: heartbeat, TLS/domain/TCP checks, synthetic transactions, deploy
  correlation, status page, retention job. Add a 2nd check type (TCP) only as a stretch if Phase 5
  finishes early.
- Stack: Java 21, Maven, Spring Boot 4.1.x, PostgreSQL, Flyway, React+Vite (3 screens only:
  login, dashboard, check detail).
- Hosting: Fly.io (always-on free VM, avoids scale-to-zero killing the scheduler) + Neon/Supabase
  free Postgres.
- Auth: `io.jsonwebtoken:jjwt` (not the oauth2-resource-server starter) — less code for a
  self-issued JWT flow.
- Alerting: webhook only (no email).
- Testing: skip Testcontainers. Keep ~5 pure `EvaluationService` hysteresis unit tests (no Spring
  context, per Doc 1 §6.3-6.4) + 2 security tests (cross-tenant 404, SSRF rejection).
- Package-by-feature layout: `check/`, `probe/`, `engine/`, `incident/`, `alert/`, `security/`,
  `common/` under `in.sentra` (per source plan §1.4 and Doc 1 §3).

## Key reference snippets already extracted from Document 1 (don't re-read the PDF for these)

- `Probe` interface: `CheckType supports(); ProbeResult execute(Check check);` — `ProbeResult`
  record: `(boolean success, long latencyMs, String detail, Integer daysRemaining)`.
- `Check` entity: JSONB `config` via `@JdbcTypeCode(SqlTypes.JSON)` on `Map<String,Object>`.
- Scheduler: **claim `next_run_at` before dispatching**, never after (Doc 1 §6.1) — the #1
  mistake to avoid. Partial index `idx_checks_due ON checks (next_run_at) WHERE enabled`.
- `SsrfGuard`: scheme allowlist (http/https only) + resolve hostname, reject
  loopback/link-local/site-local/any-local/multicast addresses (Doc 1 §7.3). Validate at both
  check-creation and probe time (DNS can change after creation).
- Security: two `SecurityFilterChain` beans ordered by `securityMatcher` — `/api/**` (JWT
  required) vs public paths (`/ping/**`, `/webhook/**`, `/status/**`) (Doc 1 §7.1).
- Multi-tenancy: ownership baked into the query itself (`findByIdForUser(id, userId)`), return
  404 not 403 on a cross-tenant hit — avoids confirming the resource exists (Doc 1 §7.2).
- Alert dedup: `CREATE UNIQUE INDEX uq_alert_once ON alert_deliveries (incident_id, channel,
  transition_type)` — database, not app code, guarantees at-most-once (Doc 1 §6.5).
- Hysteresis: N=3 consecutive failures to open an incident, M=2 consecutive successes to close,
  configurable per check, default (Doc 1 §6.4).

## Progress checklist

### Phase 0 — Skeleton + deploy (days 1-2)
- [x] git init
- [ ] Maven project scaffold (pom.xml: Web, JPA, Postgres driver, Validation, Actuator, Flyway
      + flyway-database-postgresql)
- [ ] `V1__init.sql` (`app_user`, `project`)
- [ ] `GET /api/v1/ping` → `{"status":"ok"}`
- [ ] Local Postgres via Docker, migration runs clean, app boots
- [ ] `.gitignore`, first commit
- [ ] Deploy to Fly.io against Neon/Supabase Postgres
- [ ] Exit check: public `curl` returns 200; `/actuator/health` DB is `UP`

### Phase 1 — One HTTP check end-to-end (days 3-4)
- [ ] `V2__checks.sql` (`checks`, `check_runs`)
- [ ] `Check` entity, `CheckType` enum, JSONB `config`
- [ ] DTOs (never expose entity), Bean Validation
- [ ] `Probe` interface + `HttpProbe` (explicit connect/read timeouts)
- [ ] `POST /checks`, `GET /checks`, `GET /checks/{id}`, `POST /checks/{id}/run`
- [ ] Global `@ControllerAdvice`
- [ ] Exit check: create + manual run stores a `check_runs` row with latency; broken URL stores
      categorised failure not a stack trace

### Phase 2 — The engine (days 5-6)
- [ ] `next_run_at`/`interval_seconds`/`enabled` + partial index
- [ ] `@EnableScheduling` + `CheckScheduler` (claim-before-probe)
- [ ] Bounded `ThreadPoolTaskExecutor`
- [ ] `ProbeRegistry` (`Map<CheckType,Probe>` via injected `List<Probe>`)
- [ ] `Clock` bean
- [ ] Exit check: 60s check left alone 10 min → ~10 run rows unattended; hung check doesn't
      delay others

### Phase 3 — Incidents + hysteresis (days 7-8)
- [ ] `V4__incidents.sql` (`incidents` + counters on `checks`)
- [ ] `CheckState` enum (`PENDING/HEALTHY/DEGRADED/DOWN/PAUSED`)
- [ ] `EvaluationService` (pure, no Spring context)
- [ ] Configurable thresholds (default 3/2)
- [ ] `GET /incidents`, `GET /checks/{id}/incidents`
- [ ] 5 hysteresis unit tests
- [ ] Exit check: break → 1 incident after 3 fails; fix → closes after 2 successes; tests pass

### Phase 4 — Alerting (days 9-10)
- [ ] `V5__alerts.sql` (`alert_rules`, `alert_deliveries` + `uq_alert_once`)
- [ ] `Notifier` interface + `WebhookNotifier`
- [ ] `IncidentOpenedEvent`/`IncidentClosedEvent` + listener (not direct call)
- [ ] Reminder sweep, widening backoff (15/30/60/hourly)
- [ ] Exit check: break → exactly 1 webhook at 3rd failure, 1 more on recovery; forced duplicate
      blocked by DB constraint

### Phase 5 — Auth + multi-tenancy + SSRF guard (days 11-13)
- [ ] `V6__auth.sql` (password hash on `app_user`)
- [ ] Register/login, BCrypt
- [ ] jjwt issuing + validation filter
- [ ] Two `SecurityFilterChain`s (`/api/**` protected, ingest paths public)
- [ ] Retrofit every repo method to owner-scope
- [ ] `SsrfGuard`, called at creation AND inside the probe
- [ ] 2 security tests (cross-tenant 404, SSRF rejection of localhost/169.254.169.254/10.0.0.1)
- [ ] Exit check: all pass

### Day 14 — Frontend + demo prep
- [ ] Login → Dashboard (list + inline create) → Check detail (runs + incidents)
- [ ] Fallback if behind schedule: demo via REST client against raw JSON
- [ ] Rehearse: show accumulated history, break a check live, incident+alert fire, SSRF rejection
