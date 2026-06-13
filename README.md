# AMPLI-SYNC

**Offline-first data synchronization between local SQLite databases on edge/mobile clients and a central PostgreSQL backend.**

AMPLI-SYNC is the server-side sync engine for applications that must keep working without a network connection. Clients read and write a fully functional local SQLite database; the engine reconciles those changes with a central PostgreSQL database whenever connectivity is available — incrementally, transactionally, and per tenant.

It ships as a Java 17 / Jakarta REST (Jersey 3) web application packaged as a WAR and deployed on Tomcat.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## Why it exists

Field sales, field service, logistics, and distributed B2B applications can't assume a connection. Their users operate on trains, in warehouses, in basements, and on the road. AMPLI-SYNC lets you build those apps against a **local SQLite database that is always available**, and treats synchronization with the central system as a background concern that recovers cleanly after failure.

The engine is **schema-agnostic**. It does not impose a data model on you. You declare which tables participate in sync, and the engine tracks changes, ships deltas, and applies them on the other side.

**Validated in production** for mobile field-sales systems, field-service apps, distributed B2B ordering platforms, and multi-tenant SaaS — in high write-frequency environments handling thousands of records per sync cycle.

---

## How it works

```
   Mobile / Edge Client
   ┌─────────────────────┐
   │  Application logic   │
   │         ↓            │
   │  Local SQLite DB     │  ← always available, full local transactions
   └─────────┬───────────┘
             │  HTTPS + JWT  (gzip-compressed deltas)
             ↓
   ┌─────────────────────┐
   │  AMPLI-SYNC server   │  ← Jersey REST on Tomcat
   │   (this repo)        │
   │         ↓            │
   │  Central PostgreSQL  │  ← schema-per-tenant
   └─────────────────────┘
```

Synchronization is **incremental** and **bidirectional**, based on version counters and logical (soft) delete markers rather than full-table copies.

- **Push (client → server):** the client posts its local inserts, updates, and deletes as JSON to `receive-changes`. See [`sync-data-format-example.json`](sync-data-format-example.json) for the wire format.
- **Pull (server → client):** the client requests changes for a table via `sync-compressed`; the server returns a gzip-compressed delta of records modified since that device's last watermark, then the client confirms with `commit-sync`.
- **Bootstrap:** a brand-new device calls `prepopulate-db` to download a ready-to-use SQLite database seeded with its tenant's data.

Each request is authenticated by a JWT whose subject identifies the **subscriber** (user), which the server maps to a PostgreSQL **schema** (tenant). Device identity is carried separately so the server can keep an independent sync watermark per device.

---

## API

All endpoints are served under `/ampli-sync` and (except the health check) require an `Authorization` JWT header.

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/ampli-sync/` | Health check — returns version and database connectivity status |
| `GET`  | `/ampli-sync/prepopulate-db/{deviceUniqueId}` | Download a seeded SQLite database for a new device (ZIP) |
| `GET`  | `/ampli-sync/sync-compressed/{tableName}/{deviceUniqueId}` | Pull gzip-compressed changes for one table since the device watermark |
| `GET`  | `/ampli-sync/commit-sync/{syncId}` | Confirm a pulled batch was applied, advancing the watermark |
| `POST` | `/ampli-sync/receive-changes/{deviceUniqueId}` | Push client-side inserts / updates / deletes (JSON body) |

---

## Tech stack

- **Java 17**, packaged as a WAR (`mvn package`)
- **Jakarta RESTful Web Services 3.1** via **Jersey 3.1**
- **Jackson** for JSON, **gzip** for delta transport
- **PostgreSQL** as the central store (`org.postgresql` JDBC driver)
- **HikariCP** connection pooling
- **Flyway** for database migrations (run automatically on startup)
- **java-jwt (Auth0)** for token validation
- **Guava** caches for subscriber→schema and table-metadata lookups
- **Tomcat** as the servlet container ([custom-tomcat](https://github.com/AMPLIFIER-sp-z-o-o/custom-tomcat) for production)

> **Note on database support:** the engine is architected to be database-agnostic, but the current implementation targets **PostgreSQL** specifically (Flyway PostgreSQL migrations, PostgreSQL JDBC URL, `POSTGRESQL_*.sql` schema scripts). Other backends require additional work.

---

## Quick start (local development)

The fastest way to run the server is the Docker setup in [`deploy-dev/`](deploy-dev/README.md), which brings up PostgreSQL 16, Tomcat with the WAR, and a minimal seeded test database with auth disabled.

**Prerequisites:** Java 17, Maven, Docker + Docker Compose, curl.

```bash
# 1. Build the WAR and copy it as ROOT.war
./deploy-dev/build-dev.sh

# 2. Start PostgreSQL + Tomcat
cd deploy-dev/docker
docker compose up --build
```

The API is then available at `http://localhost:8080/ampli-sync/`.

```bash
# Health check — expect: "...OK! Database connected!"
curl http://localhost:8080/ampli-sync/

# Download a prepopulated SQLite DB for a test device (ZIP)
curl -OJ http://localhost:8080/ampli-sync/prepopulate-db/test-device-1
```

A smoke-test script is provided at `deploy-dev/docker/smoke-test.sh`. For remote JVM debugging, database details, and how the dev auth bypass works, see **[deploy-dev/README.md](deploy-dev/README.md)**.

---

## Configuration

The server is configured entirely through environment variables.

| Variable | Description | Default |
|----------|-------------|---------|
| `DBHOST` | PostgreSQL host | — (required) |
| `DBPORT` | PostgreSQL port | — (required) |
| `DBNAME` | Database name | — (required) |
| `DBUSER` | Database user | — |
| `DBPASS` | Database password | — |
| `DBDRIVER` | JDBC driver class | `org.postgresql.Driver` |
| `WORKING_DIR` | Directory for generated files (SQLite exports, logs) | `../working-dir/` |
| `JWT_SHARED_SECRET` | Secret used to validate client JWTs | — |
| `PACKAGE_SIZE` | Max records per sync batch | `5000` |
| `HISTORY_DAYS` | Retention window for change history | `1` |
| `DATE_FORMAT` / `TIMESTAMP_FORMAT` | Server-side date/timestamp formats | `yyyy-MM-dd HH:mm:ss` |
| `LOG_LEVEL` | Verbosity | `5` |
| `AUTH_DISABLED` | **Local dev only** — bypass JWT validation | `false` |
| `DEV_USER_ID` | User id assumed when auth is disabled | `1` |

> ⚠️ **Never set `AUTH_DISABLED=true` outside local development.** It disables JWT validation entirely.

Flyway migrations run automatically on startup (`baselineOnMigrate`, `repair`, `migrate`), so the schema is brought up to date when the application boots.

---

## Conflict resolution

AMPLI-SYNC does not force a single conflict strategy. Depending on how you model your tables and apply incoming changes, you can implement:

- **Last Write Wins** — version-counter based
- **Server authority** — server overrides the client
- **Client authority** — the edge device wins
- **Custom merge** — domain-specific reconciliation (recommended for enterprise systems)

---

## Operational characteristics

- **Stateless sync endpoints** — scale horizontally behind a load balancer
- **Per-device watermarks** — incremental delta queries, no full-table transfers
- **gzip-compressed payloads** and batched transmission (`PACKAGE_SIZE`)
- **Idempotent reprocessing** — endpoints tolerate retries and duplicate submissions
- **Resume from last successful watermark** after network interruption
- **Multi-tenant by schema** — each subscriber is mapped to a PostgreSQL schema, with caching for the subscriber→schema lookup

---

## Repository layout

```
ampli-sync/                  ← Maven module (the sync engine WAR)
  src/main/java/.../amplisync/
    SyncAPI3.java            ← REST endpoints
    SQLiteSyncConfig.java    ← env-based configuration + Flyway bootstrap
    JwtTokenValidator.java   ← JWT → subscriber resolution
    RestAuthenticationFilter.java
    SyncServer/Synchronization/  ← core engine: SyncService, Database, prepopulate, etc.
  src/main/resources/        ← POSTGRESQL_*.sql, Flyway migrations, log4j2.xml
deploy/                      ← production Docker assets
deploy-dev/                  ← local Docker Compose dev environment
build.sh / build-win.sh      ← convenience build scripts
sync-data-format-example.json ← example push payload
```

---

## Related projects

- **[ampli-sync-rn-example](https://github.com/AMPLIFIER-sp-z-o-o/ampli-sync-rn-example)** — example React Native client implementation
- **[custom-tomcat](https://github.com/AMPLIFIER-sp-z-o-o/custom-tomcat)** — Tomcat configuration for serving the WAR in production

---

## Implementation philosophy

AMPLI-SYNC is a **framework, not a turnkey product.** Adopting it means doing real engineering work: domain modeling, defining your sync and conflict strategies, and preparing infrastructure. It is built for engineering teams shipping scalable distributed systems — not for drop-in installation.

---

## License

[MIT](LICENSE).

## Commercial support

Architectural and integration support is available for commercial and enterprise deployments. Open an issue or reach out to [AMPLIFIER](https://github.com/AMPLIFIER-sp-z-o-o).
