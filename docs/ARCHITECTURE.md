# Architecture

## Purpose and boundaries

Profile Directory is an administrator-only web application for maintaining
managed user profiles and their one-to-many addresses. A managed profile is
business data, not a login identity. Local administrator accounts are the only
identities that can authenticate in version 1.

The application is deliberately a modular Spring MVC monolith rather than a
microservice system. It keeps the deployment and transactional model small
while enforcing feature boundaries that can be extracted later if justified by
scale or ownership needs.

Out of scope: public registration, password reset, SSO, uploads, bulk import,
hard-delete/purge, and an audit-event UI.

## System context

```text
                         same HTTPS origin
+----------------+    /api/v1/*     +-------------------------+
| React + MUI SPA | ----------------> | Nginx / reverse proxy   |
| browser client  | <---------------- | static UI + API gateway |
+----------------+   HTML / cookies  +------------+------------+
                                                 |
                                                 | private network
                                                 v
                                      +-------------------------+
                                      | Spring Boot 4 / Java 21 |
                                      | REST API                |
                                      +------------+------------+
                                                   |
                                                   v
                                      +-------------------------+
                                      | PostgreSQL 17.6         |
                                      | Liquibase-managed schema|
                                      +-------------------------+
```

The browser communicates with the API using same-origin requests and
`credentials: "include"`. Tokens never enter browser storage. Nginx serves the
single-page application and proxies only `/api/*`; operational endpoints stay
on the private service network.

## Repository and module structure

```text
backend/       Spring Boot API, Liquibase XML changelogs, Java tests, Dockerfile
frontend/      Vite/React/MUI application, browser tests, Dockerfile
design/        Reviewable HTML mockups and optional screenshots
design-system/  The approved Profile Directory visual source of truth
infra/         Nginx configuration and production Compose example
docs/          Architecture, security, API, testing, UI, submission source
scripts/       Repeatable local validation and PDF rendering helpers
output/pdf/    Generated deliverables (ignored by Git)
```

The backend is feature-first. Each feature keeps API/controller, application
service, domain model, and infrastructure/repository code together:

```text
auth/       login, JWT verification, refresh rotation, session revocation
users/      profile list/detail/create/update/soft-delete/restore
addresses/  nested address commands and primary-address invariant
audit/      append-only audit event recording
shared/     errors, pagination, ETags, tracing, configuration, security glue
```

JPA entities are internal persistence models. Controllers expose explicit
request/response records so database changes do not silently alter the public
contract.

## Request and write flow

```text
Request -> request ID/filter -> JWT cookie filter -> CSRF filter
        -> controller -> application service -> transactional repository
        -> audit append -> response DTO + ETag
                         \-> ProblemDetail on expected failures
```

Writes are transactional. A successful domain change and its audit event commit
together. Requests with stale or missing optimistic-concurrency ETags are
rejected before mutation.

## Data model

| Table | Responsibility | Important constraints |
| --- | --- | --- |
| `admin_accounts` | Local administrator credentials and active status | normalized unique email; BCrypt hash; ADMIN role |
| `refresh_sessions` | Rotating refresh-token session state | digest only, family ID, expiry, revocation and reuse fields |
| `user_profiles` | Managed profile data | public UUID; normalized email unique even when soft-deleted; version column |
| `addresses` | A profile's ordered addresses | public UUID; profile FK; version column; one active primary address per profile |
| `audit_events` | Immutable security and change history | append-only actor/action/target/time/request ID/redacted metadata |

`user_profiles.deleted_at` and `addresses.deleted_at` implement soft delete.
Directory queries show active profiles by default; a profile detail includes its
ordered active and archived addresses, with archived rows marked `deleted`.
Restoration reuses the original identity; the database never permits a second
profile with the same normalized email.

Liquibase is the only schema writer. Its XML master changelog includes focused
child XML changelogs for schema objects and constraints. Hibernate validates
mappings in production but does not create, update, or drop tables. Changelogs
include indexes and the partial unique index that allows at most one active
primary address per profile.

The current master is
`backend/src/main/resources/db/changelog/db.changelog-master.xml`; it includes
`changes/001-initial-schema.xml`. Spring Boot loads it through
`spring.liquibase.change-log`. Base configuration lives in
`application.properties`, with `application-dev.properties`,
`application-test.properties`, and `application-prod.properties` providing the
three profile overrides; YAML configuration is intentionally not used.

## Environment profiles

| Profile | Intended use | Database and operational behavior |
| --- | --- | --- |
| `dev` | Local development | Root Docker Compose PostgreSQL, seed/bootstrap admin, HTTP-safe local cookies, Swagger enabled |
| `test` | Automated tests | Testcontainers PostgreSQL 17.6, Liquibase XML changelogs, deterministic test configuration, Swagger enabled |
| `prod` | Deployed service | environment/secret-managed configuration, secure cookies, structured/redacted logs, private Actuator, Swagger disabled by default |

Configuration follows the twelve-factor boundary: code owns safe defaults in
`application.properties` plus profile-specific `application-<profile>.properties`;
environment variables or mounted secrets supply credentials, signing keys,
origins, and token pepper. The root `.env.example` is a local template only.
For direct local Maven runs, `spring.config.import=optional:file:../.env[.properties]`
imports the ignored root `.env`; the optional import is absent in container
deployments unless a platform injects configuration separately.

## Deployment and operations

For local work, the root `docker-compose.yml` runs only PostgreSQL and binds it
to `127.0.0.1`. The application processes run directly to preserve fast
feedback. `infra/docker-compose.prod.yml` documents a three-container topology
for a single host; a real deployment should replace its environment file with a
secret manager and terminate TLS at the ingress.

In production, the public API stays on port 8080 and only private health and
metrics Actuator endpoints run on port 8081; the browser-facing proxy blocks
all `/actuator/*` routes. Logs are structured and carry a request/trace ID, but
redact passwords, cookies, JWTs, refresh tokens, and PII values. The API uses
RFC 9457 `ProblemDetail` responses for predictable failures. OpenAPI is
generated from the live API in dev and test, and the specification is a CI
artifact.

## Design decisions

- **Same origin over permissive CORS:** reduces cookie and CSRF complexity.
- **JWT access token plus opaque rotating refresh token:** keeps requests
  stateless while retaining server-side revocation and reuse detection.
- **UUIDs plus ETags:** avoids sequential public identifiers and protects
  against accidental overwrites by concurrent editors.
- **Soft deletion:** preserves auditability and prevents accidental reuse of a
  managed identity.
- **PostgreSQL 17.6 image pin:** reproduces the assessment environment; update
  to the current supported PostgreSQL 17 patch through normal patch management.
