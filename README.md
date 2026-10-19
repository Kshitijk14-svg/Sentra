# Sentra

A unified uptime & reliability monitoring platform (college major project). See `PLAN.md` for
the build plan and progress, and the two PDFs in this folder for the full design/concept
document and the original phase-wise plan.

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL 16 · React + Vite

## Running locally

1. Start Postgres (port 55432 on the host — 5432 may already be taken by a native Postgres
   install; check with `netstat -ano | grep 5432` before assuming it's free):
   ```
   docker run -d --name sentra-db -e POSTGRES_PASSWORD=devpass -e POSTGRES_USER=sentra -e POSTGRES_DB=sentra -p 55432:5432 postgres:16
   ```
2. Run the backend:
   ```
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   The `dev` profile supplies a throwaway JWT secret. Without it the app refuses to start unless
   `JWT_SECRET` is set (at least 32 bytes).
3. Check it's alive: `curl http://localhost:8080/api/v1/ping` → `{"status":"ok"}`
4. Run the web UI (needs Node 18+; Vite forwards `/api` to the backend on 8080, so no CORS setup):
   ```
   cd frontend
   npm install
   npm run dev
   ```
   Open http://localhost:5173, create an account, and add a check. To monitor `localhost` targets or
   receive webhooks on your own machine, start the backend with `SSRF_ALLOW_PRIVATE=true`.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:55432/sentra` | Postgres JDBC URL |
| `DB_USER` | `sentra` | Postgres user |
| `DB_PASSWORD` | `devpass` | Postgres password |
| `PORT` | `8080` | HTTP port |
| `JWT_SECRET` | none (required; `dev` profile supplies a placeholder) | JWT signing secret, at least 32 bytes |
| `SSRF_ALLOW_PRIVATE` | `false` | Local demos only: lets checks/webhooks target localhost/private IPs |
| `JWT_EXPIRY_MINUTES` | `60` | Access token lifetime |
