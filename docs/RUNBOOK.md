# Runbook

Operational notes for running HarakaPay outside an IDE.

## Environment variables

| Variable | Required | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `local` for PostgreSQL, `test` for in-memory H2 (CI only) |
| `HARAKA_JWT_SECRET` | yes in prod | ≥ 32 bytes of entropy; startup fails fast on weak values |
| `HARAKA_PG_HOST` / `HARAKA_PG_PORT` / `HARAKA_PG_DB` / `HARAKA_PG_USER` / `HARAKA_PG_PASSWORD` | for `local` | PostgreSQL connection |
| `HARAKA_JWT_TTL_MINUTES` | no | token lifetime, default 120 |
| `HARAKA_IDEMPOTENCY_RETENTION_DAYS` | no | default 7 |
| `PORT` | no | HTTP port, default 8080 |

## Health model

- `/actuator/health` aggregates `db` and `ledgerIntegrity`.
- `ledgerIntegrity` recomputes every wallet balance from the ledger on each health check.
  If they diverge the whole endpoint reports `DOWN` and returns HTTP 503, which makes any
  orchestrator (Docker, Kubernetes, load balancers) pull the instance out of rotation.
- `/actuator/prometheus` exposes JVM and HTTP metrics; scrape it for latency/error dashboards.

## Data lifecycle

- **Idempotency records**: retained 7 days (`HARAKA_IDEMPOTENCY_RETENTION_DAYS`), purged by a
  scheduled job every 6 hours (first run 1 minute after startup). This also cleans markers left
  by crashed operations.
- **Ledger/transfer rows**: never deleted; they are the audit trail.
- **Migrations**: applied by Flyway at startup; never edit an applied migration, always add
  `V{n}__description.sql`.

## Local troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Boot fails with `HARAKA_JWT_SECRET must be at least 32 bytes` | weak signing secret | set a long random value |
| `Connection refused` on startup | PostgreSQL not running | `docker compose up -d postgres` |
| 401 on every call | missing/expired bearer token | re-login |
| `409 CONTENTION` on transfers | extreme contention | client retry with the same `Idempotency-Key` is safe |

## Performance notes

- Statement reads are index-backed (`idx_ledger_entry_account`) and bounded to 100 rows per page.
- The transfer lock query touches exactly two rows in primary-key order.
- Hikari pool: 10 connections by default in `local`; size for `threads < pool` so
  `REQUIRES_NEW` audit/reservation transactions never starve.
