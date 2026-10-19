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
