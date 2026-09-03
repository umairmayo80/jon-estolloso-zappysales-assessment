# Profile Directory

An admin-only profile directory for viewing and modifying managed user profiles
and their one-to-many addresses. The backend is Java 21/Spring Boot 4 with
PostgreSQL/Liquibase; the frontend is React 19 with MUI. It uses cookie-based JWT
access authentication, rotating refresh tokens, CSRF protection, soft deletion,
ETags, and an append-only audit trail.

## Repository layout

| Path          | Purpose                                                             |
| ------------- | ------------------------------------------------------------------- |
| `backend/`  | Spring Boot API, Liquibase XML changelogs, Java tests, Dockerfile   |
| `frontend/` | React/MUI application, tests, Dockerfile                            |
| `design/`   | Static UI mockups; Directory Desk is the selected direction         |
| `infra/`    | Nginx and production topology example                               |
| `docs/`     | Architecture, security, API, testing, UI, and PDF submission source |
| `scripts/`  | Mockup preview and PDF-render helpers                               |

## Prerequisites

- Docker Engine with Docker Compose v2
- JDK 21 (the project requires Java 21; Java 17 is not sufficient)
- Node 22.13.1 and npm 10+ (`.nvmrc` declares the local Node version)
- For backend integration tests, Docker must be running for Testcontainers
- Optional PDF delivery tools: Pandoc, XeLaTeX, and Poppler (`pdftoppm`)

Check the core tools:

```bash
java -version
node --version
npm --version
docker compose version
```

## Start locally

1. Create a local-only environment file and start PostgreSQL 17.6:

   ```bash
   cp .env.example .env
   docker compose --env-file .env up -d postgres
   docker compose ps
   ```

   PostgreSQL is available only at `127.0.0.1:5432`. To inspect readiness:

   ```bash
   docker compose exec postgres pg_isready -U profile_directory -d profile_directory
   ```
2. Run the backend. The `dev` profile is the local default:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   The API runs at [http://localhost:8080](http://localhost:8080). Set
   `SPRING_PROFILES_ACTIVE=prod` explicitly for production deployments. Dev starts with demo data
   and, by default, one local-only administrator:

   ```text
   email:    admin@example.test
   password: ChangeMe123!
   ```

   The backend optionally imports `../.env` as a properties file when launched
   from `backend/`, so changes to local credentials/configuration are actually
   applied. In dev, blank JWT key paths intentionally create an ephemeral RSA
   key; do not add development key files unless that is explicitly needed.
   Do not use the sample administrator anywhere outside development. Swagger UI
   is available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html); the OpenAPI document
   is at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).
3. Run the frontend in a second terminal:

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

   Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` to the local backend, so
   browser requests remain same-origin from the application's perspective.

The dev profile has safe local cookie settings. Production requires HTTPS and
`APP_COOKIE_SECURE=true`.

## Tests and quality checks

```bash
# Backend: unit, web/security, and Testcontainers integration tests
cd backend
./mvnw verify

# Frontend: lint, TypeScript, component tests, production bundle
cd ../frontend
npm run lint
npm run typecheck
npm run test
npm run build

# Browser journey tests (install Chromium once)
npx playwright install --with-deps chromium
npm run test:e2e
```

`Testcontainers` starts a disposable PostgreSQL 17.6 database for backend
integration tests. It does not depend on the local Compose database.

## Database migration model

Liquibase owns the schema. The XML master changelog is
`backend/src/main/resources/db/changelog/db.changelog-master.xml` and includes
focused child changelogs under `changes/`. Spring configuration is exclusively
`.properties` files (`application.properties` plus the three named profile
overrides); no Spring YAML configuration is used. Hibernate validates the
database mapping and never generates production schema changes.

## User → address flow

The directory at `/users` is optimized for finding a user through URL-backed
search, status, sorting, and paging. Opening a user moves to a profile workspace
that keeps profile information and that user’s addresses together. On desktop,
profile/address creation and edits are route-backed drawers; on mobile they are
full-screen routes. Returning to the list retains its query and scroll context.

Each user can have multiple ordered addresses with at most one active primary
address. Deletes are soft deletes, and ETags prevent one administrator from
silently overwriting another administrator’s recent change.

## Security at a glance

- `PD_ACCESS`: short-lived RS256 access JWT in an `HttpOnly`, host-only cookie.
- `PD_REFRESH`: rotating opaque refresh token in a narrower `HttpOnly`,
  host-only cookie; only a keyed digest is kept in PostgreSQL.
- `XSRF-TOKEN` + `X-XSRF-TOKEN`: required for every unsafe request.
- Both authenticated tokens are absent from browser storage and API JSON.
- Refresh reuse revokes the full session family and records an audit event.

See [Security design](docs/SECURITY.md) and [API contract](docs/API.md) for the
full contract.

## Production topology

The intended public shape is one HTTPS origin:

```text
browser -> HTTPS ingress/Nginx (React build + /api proxy) -> Spring Boot -> PostgreSQL
```

The root `docker-compose.yml` is intentionally only a local database dependency.
The example `infra/docker-compose.prod.yml` builds frontend/backend containers,
keeps PostgreSQL private, and mounts JWT key files as read-only Docker secrets.
Validate its interpolation only after loading the real production secret
environment (the example intentionally refuses to resolve without required
credentials and key paths):

```bash
docker compose --env-file /secure/path/profile-directory.env \
  -f infra/docker-compose.prod.yml config
```

Use a secret manager for production keys/credentials, terminate TLS at the
public ingress, disable bootstrap administration after first use, and keep
Actuator private. See [infra deployment notes](infra/README.md).

## Documentation and PDF submission

- [Architecture](docs/ARCHITECTURE.md)
- [Security](docs/SECURITY.md)
- [API](docs/API.md)
- [Testing](docs/TESTING.md)
- [UI design](docs/UI-DESIGN.md)
- [Submission source instructions](docs/submission/README.md)

Render review screenshots from the static mockups:

```bash
scripts/render-mockup-previews.sh
```

The helper launches headless Chrome with an isolated temporary profile and
`--no-sandbox` only to render repository-owned static HTML. Do not repurpose it
to browse untrusted pages.

Prepare the required submission filename, then render its PDF and page previews:

```bash
scripts/render-submission-pdf.sh --check-tools
scripts/render-submission-pdf.sh docs/submission/LastName_FirstName_AssessmentForFullStackDeveloper_2026-09-03.md
```

Generated PDFs and preview images go under `output/pdf/` and are ignored by
Git. See the submission README before final delivery.

## Troubleshooting

- **`release version 21 not supported`** — activate/install JDK 21 and verify
  `java -version` before invoking Maven.
- **Testcontainers cannot start** — start Docker and make sure your user can
  access its socket.
- **Frontend gets a 401 after a backend restart** — sign in again; the local
  server session/key material may have changed.
- **Port 5432/5173/8080 already in use** — stop the local process using that
  port or change the relevant local configuration. Do not expose PostgreSQL on
  a public interface.
