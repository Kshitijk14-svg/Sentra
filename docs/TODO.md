# Still to do

What is built so far: HTTP checks, TCP port checks, TLS certificate checks, incidents with hysteresis,
webhook alerting, auth and tenant isolation, SSRF guard, and the React frontend. This file lists
what is not done yet.

## Features not built

- [ ] **Domain expiry check.** A new `DOMAIN` check type that reads the registration expiry date over
  RDAP (JSON over HTTPS), runs daily, and fails inside a warning window, like `TlsProbe`. RDAP servers
  differ per TLD and some are unreliable, so an unclear answer should use the `inconclusive` result
  instead of failing the check.
- [ ] **Database query health check** (optional, future work). A real login plus `SELECT 1`. A TCP
  check on port 5432 or 3306 already covers "is the port reachable". The query check needs credentials
  stored encrypted (the `*Enc` config key convention in `CheckResponse` is reserved for this) and a
  stricter SSRF review.

## Unfinished or unverified

- [ ] Browser test of the new UI: the TCP and TLS create form, grouping by site, and the run detail
  text. The Playwright browser kept closing, so retry with Claude in Chrome or test by hand at
  `localhost:5173`.
- [ ] The `inconclusive` run flag is wired through the database and `IncidentService`, but no probe
  sets it yet. Domain expiry would be the first user.
- [ ] The UI shows TLS facts (days until expiry) only as run detail text.

## Housekeeping

- [ ] `PLAN.md`: tick the stale unchecked Phase 0 and Phase 1 boxes. The Phase 6 demo rehearsal is
  still open.
- [ ] Add `backend-run.log` and `frontend-run.log` to `.gitignore`, or delete them.
- [ ] The test JVM can crash on low native memory. Run tests with
  `-DargLine="-Xmx512m -XX:MaxMetaspaceSize=256m"`.
