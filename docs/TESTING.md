# Testing strategy

## Quality gates

| Layer | Tooling | Primary evidence |
| --- | --- | --- |
| Java unit | JUnit 5, Mockito as needed | domain rules and application-service behavior |
| Java web/security | Spring MVC/security test support | auth, CSRF, authorization, errors, cookies, ETags |
| Java integration | Testcontainers PostgreSQL 17.6 + Liquibase XML | real SQL, changelogs, transactional/audit behavior |
| React unit/component | Vitest, React Testing Library, MSW | rendering, validation, loading/error recovery, client behavior |
| Accessibility | automated checks plus keyboard review | labels, focus, dialogs, color-independent states |
| Browser E2E | Playwright | critical user journey across responsive breakpoints |
| Delivery | Docker Compose config, Docker builds, PDF script | reproducible execution and submission output |

## Required scenarios

### Backend

- Liquibase applies its XML master/child changelogs to a blank PostgreSQL 17.6 instance and validates schema
  constraints.
- Login gives a generic failure for incorrect/inactive/unknown administrators,
  persists a redacted failed-login audit event outside the rejected transaction,
  sets only expected cookie attributes on success, and enforces rate limits.
- Unsafe requests without a matching CSRF header fail with `403`.
- Access-token expiry renews through a valid refresh token; refresh rotation
  invalidates the previous value; reused refresh token revokes its entire
  family and writes an audit event.
- Unauthenticated and non-ADMIN requests receive correct RFC 9457 errors.
- User query paging/search/sort validation, duplicate emails, soft deletion,
  restoration, address lifecycle, primary-address invariant, and audit writes
  work against PostgreSQL.
- Missing and stale `If-Match` values return `428` and `412` without mutation.
- Oversized JSON requests return `413` before CSRF/deserialization; chunked
  bodies are counted by the backend stream wrapper as a direct-service guard.
- The test profile uses committed, test-only RSA fixtures rather than an
  ephemeral key pair, making signed-token assertions reproducible.

### Frontend

- Login requests CSRF first, uses cookies/`credentials: include`, and never
  writes a token to local or session storage.
- Directory search, status, sort, page, loading skeleton, empty state, and API
  error state render predictably.
- Profile and address forms show visible labels, inline validation, linked
  error summary, saved feedback, and destructive confirmation.
- Returning from profile/editor routes preserves list URL state and scroll
  position.
- A `412` response retains the unsaved draft and provides refresh/retry
  recovery rather than overwriting data.
- Keyboard flow, focus restoration, icon labels, reduced motion, 390/768/1024/
  1440px layouts, and no mobile horizontal overflow are verified.

## Local commands

```bash
# Prerequisite for integration tests and local development database
cp .env.example .env
docker compose --env-file .env up -d postgres

# Backend (JDK 21 and Docker required for Testcontainers)
cd backend
./mvnw verify

# Frontend (Node version declared in ../.nvmrc)
cd ../frontend
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

Run browser tests after the frontend test configuration's required server/API
fixtures are available:

```bash
cd frontend
npx playwright install --with-deps chromium
npm run test:e2e
```

## Continuous integration

GitHub Actions executes formatting/lint/type checks, Maven verification,
frontend tests/build, Docker image builds, OpenAPI artifact capture, and PDF
render validation. A database container is not shared between test jobs;
backend integration tests create their own disposable Testcontainers instance.

When a test fails, retain the backend Surefire reports, frontend coverage/test
reports, Playwright report, generated OpenAPI document, and PDF page previews
as CI artifacts when available.
