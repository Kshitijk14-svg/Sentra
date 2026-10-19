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
   mvn spring-boot:run
   ```
3. Check it's alive: `curl http://localhost:8080/api/v1/ping` → `{"status":"ok"}`

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:55432/sentra` | Postgres JDBC URL |
| `DB_USER` | `sentra` | Postgres user |
| `DB_PASSWORD` | `devpass` | Postgres password |
| `PORT` | `8080` | HTTP port |
| `JWT_SECRET` | dev placeholder | JWT signing secret — set a real one before deploying |
| `JWT_EXPIRY_MINUTES` | `60` | Access token lifetime |
