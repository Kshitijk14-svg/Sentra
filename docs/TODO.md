# Still to do

What is built so far: HTTP checks, TCP port checks, TLS certificate checks, domain expiry checks (RDAP),
cron/heartbeat checks, incidents with hysteresis, webhook alerting, auth and tenant isolation, SSRF
guard, and the React frontend (with pause/resume/delete). This file lists what is not done yet.

## Features not built

- [ ] **WHOIS fallback for domain checks** on TLDs without RDAP (e.g. `.de`). Those runs are
  `inconclusive` today, so they never alert.
- [ ] **Database query health check** (optional, future work). A real login plus `SELECT 1`. A TCP
  check on port 5432 or 3306 already covers "is the port reachable". The query check needs credentials
  stored encrypted (the `*Enc` config key convention in `CheckResponse` is reserved for this) and a
  stricter SSRF review.

## Unfinished or unverified

- [ ] Browser test of the new UI: the TCP and TLS create form, grouping by site, and the run detail
  text. The Playwright browser kept closing, so retry with Claude in Chrome or test by hand at
  `localhost:5173`.
- [ ] The UI shows TLS facts (days until expiry) only as run detail text.

## Housekeeping

- [ ] `PLAN.md`: tick the stale unchecked Phase 0 and Phase 1 boxes. The Phase 6 demo rehearsal is
  still open.
- [ ] Add `backend-run.log` and `frontend-run.log` to `.gitignore`, or delete them.
- [ ] The test JVM can crash on low native memory. Run tests with
  `-DargLine="-Xmx512m -XX:MaxMetaspaceSize=256m"`.
