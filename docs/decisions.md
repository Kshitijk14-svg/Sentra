# Design decisions

- **Java 21 + Spring Boot 4.1, Maven**: mainstream, well-documented stack for the viva.
- **PostgreSQL 16 + Flyway, `ddl-auto: validate`**: schema changes are versioned migrations, never auto-generated.
- **JVM timezone forced to UTC** (`SentraApplication`): Postgres rejects the `Asia/Calcutta` zone id.
- **Webhook-only alerting**: no email provider, no paid keys.
- **Hysteresis for incidents**: N consecutive failures open an incident, M consecutive successes resolve it, to avoid flapping alerts.
- **SSRF guard**: resolve DNS first, then block private/loopback/link-local addresses.
- **Host Postgres port 55432**: avoids clashing with a native Postgres on 5432.
