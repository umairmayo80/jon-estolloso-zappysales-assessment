---
title: "Profile Directory"
subtitle: "Assessment for Full Stack Developer"
author: "Sardar Umair Tariq"
date: "2026-09-03"
lang: en-US
geometry: margin=0.72in
fontsize: 10.5pt
colorlinks: true
linkcolor: blue
urlcolor: blue
---

# Executive summary

Profile Directory is an administrator-only web application for finding,
reviewing, and maintaining user profiles and their one-to-many addresses. The
solution uses Java 21 and Spring Boot 4 for the API, PostgreSQL 17.6 and
Liquibase for persistent storage, and React 19 with Material UI for the
browser application.

The implementation focuses on a practical administrative workflow. An
administrator signs in, filters or searches the directory, opens a profile,
and manages that profile and its addresses without losing the directory state.
Profile and address changes are protected by validation, optimistic concurrency
with ETags, soft delete and restore, audit records, CSRF protection, and
cookie-based authentication.

This document is self-contained. It includes the local setup, run, test,
capture, and PDF-generation commands needed to review the submission.

# Requirement traceability

| Requirement | Delivered implementation |
| --- | --- |
| Java 17+ and Spring Boot backend | Java 21, Spring Boot 4, feature-based modules, explicit request/response DTOs, and versioned REST endpoints |
| React and MUI frontend | React 19, MUI components and theme, MUI X Data Grid on desktop, and responsive profile cards on narrower screens |
| User list | Searchable, sortable, paginated directory showing name, email, status, address count, and last update |
| User modification flow | Route-backed create and edit forms, validation, conflict recovery, and return-state preservation |
| One user to many addresses | Profile detail workspace with ordered active and archived address groups plus nested address create, edit, archive, and restore actions |
| Clear navigation and UI state | URL-backed directory query state, deep links, route-backed editors, browser Back protection for dirty forms, and restored list context |
| Clean, responsive interface | Desktop grid, tablet and mobile cards, desktop drawers, mobile full-screen dialogs, accessible focus behavior, and light/dark themes |
| Submission evidence | This report, live application screenshots, README preview gallery, automated checks, and a rendered PDF |

# Architecture and data model

```text
Browser: React + Material UI
        |
        | same-origin HTTPS, HttpOnly cookies
        v
Nginx: static SPA + /api reverse proxy
        |
        v
Spring Boot 4 / Java 21
  auth | users | addresses | audit | shared
        |
        v
PostgreSQL 17.6 managed by Liquibase
```

The backend is a modular monolith. Each feature contains its controller,
application service, domain model, and repository. The frontend keeps server
state in TanStack Query, form state in React Hook Form, and navigational state
in the URL. The top-level React Router data router supports browser navigation
blocking for unsaved editor changes.

```text
admin_accounts 1 -> many refresh_sessions
admin_accounts 1 -> many audit_events
user_profiles  1 -> many addresses
```

Profiles and addresses use UUID identifiers, version columns, and soft-delete
metadata. A profile email remains reserved after archival. An active profile
can have at most one active primary address.

## API behavior

The versioned API base is `/api/v1`.

| Area | Endpoints and behavior |
| --- | --- |
| Authentication | `GET /auth/csrf`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, and `GET /auth/me` |
| Profiles | `GET /users`, `POST /users`, `GET /users/{userId}`, `PATCH /users/{userId}`, `DELETE /users/{userId}`, and `POST /users/{userId}/restore` |
| Addresses | `POST /users/{userId}/addresses`, `GET /users/{userId}/addresses/{addressId}`, `PATCH /users/{userId}/addresses/{addressId}`, `DELETE /users/{userId}/addresses/{addressId}`, and `POST /users/{userId}/addresses/{addressId}/restore` |
| Lists and errors | List requests accept query, status, approved sort fields, zero-based page, and bounded size. Errors use RFC 9457 `ProblemDetail` JSON with a stable code and request ID. |

Reads return strong ETags. Mutating profile and address operations require an
`If-Match` value. Missing preconditions return HTTP 428 and stale values return
HTTP 412, allowing the UI to retain the draft and offer the latest server
version for the next save.

## Authentication and security

```text
CSRF bootstrap -> sign in -> access JWT + refresh cookie
  -> protected request -> refresh rotation when needed
  -> refresh reuse -> revoke session family -> sign in again
```

- The short-lived access JWT uses RS256 and an `HttpOnly`, `SameSite=Strict`,
  host-only cookie scoped to `/api/v1`.
- The refresh token is opaque. The server retains only a keyed digest and
  rotates it with sliding and absolute lifetimes.
- Every unsafe request requires the readable `XSRF-TOKEN` cookie value in the
  `X-XSRF-TOKEN` request header.
- Only active administrators with the `ADMIN` role can access protected API
  operations. Login errors are generic, and authentication routes are rate
  limited.
- Mutations create audit events. Passwords, raw tokens, and sensitive cookie
  values are not written to audit data or API JSON.

# Local setup and operation

## Prerequisites

- Docker Engine and Docker Compose v2
- JDK 21
- Node 22.13.1 and npm 10 or newer
- Docker access for Testcontainers integration tests
- Poppler's `pdftoppm`, plus either native Pandoc and XeLaTeX or the
  `pandoc/latex:latest` Docker image for the PDF renderer

Check the local tools:

```bash
java -version
node --version
npm --version
docker compose version
scripts/render-submission-pdf.sh --check-tools
```

## Start the application

From a clone of the repository root, create the local environment file and
start PostgreSQL:

```bash
cp .env.example .env
docker compose --env-file .env up -d postgres
docker compose ps
docker compose exec postgres pg_isready -U profile_directory -d profile_directory
```

Run the backend in one terminal:

```bash
cd backend
./mvnw spring-boot:run
```

Run the frontend in a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

The frontend is available at `http://localhost:5173`; the API is available at
`http://localhost:8080`. Vite proxies `/api` requests to the backend during
development.

The default development administrator is local-only:

```text
display name: Sardar Umair
email:    admin@example.test
password: ChangeMe123!
```

Do not use these sample credentials outside the local `dev` profile. Production
requires HTTPS, secure cookies, configured signing keys, and
`APP_COOKIE_SECURE=true`.

## Development fixture data

With the `dev` profile and `APP_SEED_DEMO_DATA=true` (the development default),
startup adds a deterministic fictional dataset:

| Fixture | Count |
| --- | ---: |
| Active profiles | 54 |
| Archived profiles | 6 |
| Total profiles | 60 |
| Active addresses | 78 |
| Archived addresses | 1 |
| Total addresses | 79 |

All fixture emails use `example.test`. The profile contract intentionally
contains the required email, first name, and last name fields; it does not add
phone or personal-date fields that are outside the application requirements.
Addresses provide realistic location data for list, detail, archive, and
restore examples.

The seed is additive and idempotent. It identifies a fixture by its stable
email and an address by its label within that profile. Existing rows are never
overwritten, duplicated, deleted, or re-archived. Set
`APP_SEED_DEMO_DATA=false` to start dev without fixtures.

Maya Chen (`maya.chen@example.test`) is the featured record for visual review.
She has an active Office address, an active Home address, and an archived Travel
address. The archived profile fixtures make the archived filter and restore
controls available without changing a user-created record.

# User and address workflow

```text
Sign in
  |
  v
Directory: search, status filter, sort, and page controls
  |
  v
Profile detail: identity, active addresses, archived addresses
  |-- Edit profile
  |-- Add or edit address
  `-- Archive -> confirm -> archived state -> restore
  |
  v
Return to preserved directory query and scroll state
```

The list URL stores query, status, sort, page, and page size. Selecting a
profile preserves that state, so returning does not reset a search or filter.
On desktop, create and edit routes render in a right-side drawer. On mobile,
the same routes render in a full-screen MUI dialog. Both forms retain typed
values through a stale-data refetch and show inline validation plus a linked
error summary after an invalid submission.

An unsaved editor blocks browser Back, forward, and in-application route
changes. The confirmation gives the administrator two explicit choices: stay
and retain the draft, or discard and continue to the intended destination. A
successful save clears the draft and follows its detail route without a second
confirmation.

# UI design, responsive behavior, and accessibility

The UI uses a restrained business-workspace design: clear typography, generous
spacing, restrained surface borders, semantic success/destructive states, and a
single primary action per view. Desktop uses the Data Grid for high-density
scanning. Tablet and mobile replace it with touch-friendly profile cards, while
keeping name, email, status, address count, and update time visible.

| Viewport | Directory layout | Editor layout | Evidence output |
| --- | --- | --- | --- |
| Desktop, 1920 x 1080 CSS pixels | Data Grid | Right-side drawer | Literal FHD PNG |
| Tablet, 1024 x 1365 and 768 x 1024 | Profile cards | Drawer at 1024, responsive application shell | High-density native PNG |
| Mobile, 390 x 844 | Profile cards and compact header | Full-screen MUI dialog | 3x-density native PNG |

## Light and dark mode

The color-mode menu is available on the login page and inside the authenticated
application header. Its options are System, Light, and Dark.

- System is the default. It follows the operating-system preference until the
  administrator chooses another mode.
- Light mode uses a pale neutral canvas with white surfaces, dark slate text,
  cobalt actions, and distinct semantic status colors.
- Dark mode uses a deep slate canvas and elevated dark surfaces with lighter
  text, visible dividers, and the same semantic action/status roles.
- The selection is stored in local browser storage and restored before the
  application paints. The document color-scheme attribute and MUI CSS
  variables apply the selected palette without a light-to-dark flash.

The UI treats color as a supporting cue, not the sole indicator of state.
Status chips include text, form errors include text and field association, and
all interactive controls retain visible focus treatment. Palette tests check
normal text and controls against the 4.5:1 contrast target in both modes.

## Repairs made during review

| Review finding | Repair |
| --- | --- |
| Narrow profile cards hid email | Cards now show the email directly below the name. The identity row is shrinkable and long email values use safe wrapping rather than clipping or horizontal overflow. |
| Detail-header email could force layout overflow | The header identity area and email are shrinkable and use safe long-token wrapping. |
| Mobile editor used a fixed generic box | The editor now uses MUI `Dialog` with `fullScreen`. MUI manages focus trapping, Escape handling, restoration of focus, backdrop interaction, and background accessibility isolation. |
| Dirty forms did not protect browser Back | The data router and `useBlocker` now intercept route navigation, while the existing browser unload warning protects refresh and tab close. |
| Full-page route changes did not move focus | The main route heading receives focus after content renders, except while an editor overlay is open. |
| Mobile login exposed no visible top-level heading | "Welcome back" is the visible login `h1`; the decorative desktop marketing line is not a competing heading. |

# Screenshot evidence gallery

The following images were captured from the running application with the
development fixtures above. Desktop images use a 1920 x 1080 CSS-pixel FHD
viewport. Tablet and mobile images use their native responsive sizes at higher
device density. Long profile/detail states use full-page capture only when the
viewport would omit relevant content.

## Desktop light mode

![Desktop light login screen, 1920 x 1080](assets/screenshots/01-login-light-desktop.png){ height=4.7in }

![Desktop light active directory after a live Maya search, 1920 x 1080](assets/screenshots/02-directory-active-light-desktop.png){ height=4.7in }

![Desktop light all-status directory showing active and archived records, 1920 x 1080](assets/screenshots/03-directory-all-light-desktop.png){ height=4.7in }

![Desktop light archived directory filter and restore path, 1920 x 1080](assets/screenshots/04-directory-archived-light-desktop.png){ height=4.7in }

![Desktop light profile detail for Maya Chen with active and archived addresses](assets/screenshots/05-profile-detail-light-desktop.png){ height=6.2in }

![Desktop light create-profile drawer, 1920 x 1080](assets/screenshots/06-profile-create-light-desktop.png){ height=4.7in }

![Desktop light edit-profile drawer, 1920 x 1080](assets/screenshots/07-profile-edit-light-desktop.png){ height=4.7in }

![Desktop light add-address drawer, 1920 x 1080](assets/screenshots/08-address-add-light-desktop.png){ height=4.7in }

![Desktop light edit-address drawer, 1920 x 1080](assets/screenshots/09-address-edit-light-desktop.png){ height=4.7in }

![Desktop light address archive confirmation, 1920 x 1080](assets/screenshots/10-address-archive-confirm-light-desktop.png){ height=4.7in }

![Desktop light profile archive confirmation, 1920 x 1080](assets/screenshots/11-profile-archive-confirm-light-desktop.png){ height=4.7in }

![Desktop light archived profile restore confirmation, 1920 x 1080](assets/screenshots/12-profile-restore-confirm-light-desktop.png){ height=4.7in }

![Desktop light archived address restore confirmation, 1920 x 1080](assets/screenshots/24-address-restore-confirm-light-desktop.png){ height=4.7in }

## Desktop dark mode

![Desktop dark directory, 1920 x 1080](assets/screenshots/13-directory-dark-desktop.png){ height=4.7in }

![Desktop dark profile detail, 1920 x 1080](assets/screenshots/14-profile-detail-dark-desktop.png){ height=6.2in }

![Desktop dark profile editor, 1920 x 1080](assets/screenshots/15-profile-editor-dark-desktop.png){ height=4.7in }

## Tablet layouts

![Tablet light directory cards at 768 pixels wide, with email visible](assets/screenshots/16-directory-active-light-tablet-768.png){ height=6.5in }

![Tablet light archived profile cards at 1024 pixels wide](assets/screenshots/17-directory-archived-light-tablet-1024.png){ height=5.8in }

![Tablet light profile editor at 1024 pixels wide](assets/screenshots/25-profile-editor-light-tablet-1024.png){ height=5.8in }

## Mobile layouts

![Mobile light login screen, 390 x 844 at 3x density](assets/screenshots/18-login-light-mobile.png){ height=7.2in }

![Mobile dark profile directory cards with visible email, 390 x 844 at 3x density](assets/screenshots/19-directory-dark-mobile.png){ height=7.2in }

![Mobile dark profile detail, 390 x 844 at 3x density](assets/screenshots/20-profile-detail-dark-mobile.png){ height=7.2in }

![Mobile dark full-screen profile editor, 390 x 844 at 3x density](assets/screenshots/21-profile-editor-dark-mobile.png){ height=7.2in }

![Mobile dark full-screen address editor, 390 x 844 at 3x density](assets/screenshots/22-address-editor-dark-mobile.png){ height=7.2in }

![Mobile dark unsaved-change confirmation after navigation, 390 x 844 at 3x density](assets/screenshots/23-dirty-confirm-dark-mobile.png){ height=7.2in }

![Mobile dark address archive confirmation, 390 x 844 at 3x density](assets/screenshots/26-address-archive-confirm-dark-mobile.png){ height=7.2in }

\clearpage

# Reproducing the screenshots

The capture workflow uses an isolated PostgreSQL volume and port. It does not
reuse or erase the ordinary local development database.

Start the capture database from the repository root:

```bash
CAPTURE_POSTGRES_PORT=5433 docker compose \
  -p profile-directory-capture \
  -f docker-compose.yml \
  -f docker-compose.capture.yml \
  up -d postgres
```

Start the backend against that isolated database in one terminal:

```bash
cd backend
DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/profile_directory \
DATABASE_USERNAME=profile_directory \
DATABASE_PASSWORD=profile_directory \
SPRING_PROFILES_ACTIVE=dev \
APP_SEED_DEMO_DATA=true \
APP_BOOTSTRAP_ENABLED=true \
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.test \
APP_BOOTSTRAP_ADMIN_PASSWORD=ChangeMe123! \
APP_BOOTSTRAP_ADMIN_DISPLAY_NAME='Sardar Umair' \
APP_COOKIE_SECURE=false \
SERVER_PORT=8081 \
./mvnw spring-boot:run
```

Start the frontend in a second terminal:

```bash
cd frontend
npm ci
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 \
npm run dev -- --host 127.0.0.1 --strictPort
```

Run the capture script in a third terminal. A standard Playwright Chromium
installation uses the first command. The second command is suitable for a
constrained environment with a system Chrome executable:

```bash
cd frontend
npx playwright install chromium
npm run capture:docs
```

```bash
cd frontend
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/google-chrome \
CAPTURE_BASE_URL=http://127.0.0.1:5173 \
npm run capture:docs
```

The script writes only the numbered PNGs in
`docs/submission/assets/screenshots/`. It signs in with the development
administrator, locates the fixtures through the live UI/API, selects light or
dark mode, verifies headings and responsive width, and never confirms an
archive action.

When capture is complete, stop and remove only the isolated capture resources:

```bash
CAPTURE_POSTGRES_PORT=5433 docker compose \
  -p profile-directory-capture \
  -f docker-compose.yml \
  -f docker-compose.capture.yml \
  down -v
```

# Verification evidence

Run the complete verification suite from the repository root:

```bash
cd backend
./mvnw verify
```

```bash
cd frontend
npm run lint
npm run typecheck
npm run test
npm run build
npm run test:e2e
```

If the environment does not contain Playwright's managed Chromium, run the
browser tests with the system executable:

```bash
export PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/google-chrome
npm run test:e2e
```

| Area | Final result |
| --- | --- |
| Backend JUnit | 23 of 23 passed. This includes 14 unit/configuration cases and 9 Testcontainers integration cases. |
| Backend integration tests | 9 of 9 passed against disposable PostgreSQL 17.6 with Liquibase migrations. |
| Frontend Vitest | 11 of 11 component/unit cases passed across 4 test files. |
| Frontend Playwright | 14 authored browser journeys expanded into 28 desktop/mobile project instances: 17 passed and 11 expected device-specific skips. |
| Static frontend checks | ESLint passed with zero warnings, TypeScript typecheck passed, and the production Vite build passed. |
| Visual evidence | 26 live PNGs were captured from the isolated seeded app. The capture asserts visible directory emails and no horizontal overflow; desktop, tablet, mobile, dialogs, and both color modes were visually inspected. |

The Playwright total separates authored journeys from browser-project expansion,
so expected desktop-only or mobile-only skips are not represented as missing
coverage.

# PDF generation and delivery

Render the final report and page previews from the repository root:

```bash
scripts/render-submission-pdf.sh --check-tools
scripts/render-submission-pdf.sh
```

If native Pandoc and XeLaTeX are unavailable, use the container fallback once:

```bash
docker pull pandoc/latex:latest
scripts/render-submission-pdf.sh --check-tools
scripts/render-submission-pdf.sh
```

The PDF is written to:

```text
output/pdf/Tariq_Sardar_Umair_AssessmentForFullStackDeveloper_2026-09-03.pdf
```

The renderer also writes PNG page previews beneath `output/pdf/previews/`. The
final inspection checks image sharpness, table width, heading flow, page breaks,
captions, and that no content is clipped or overlaps.

\clearpage

# Submission checklist

- [x] Assessment filename and author metadata are complete.
- [x] The report contains direct setup, run, test, capture, and PDF commands.
- [x] Development fixtures are fictional, deterministic, and limited to dev.
- [x] The screenshot gallery covers desktop, tablet, mobile, light mode, dark mode, and profile/address flows.
- [x] Responsive email visibility, mobile modal behavior, unsaved navigation protection, and route-focus behavior are documented and tested.
- [x] Verification totals are recorded from the completed final runners.
- [x] The PDF and page previews were rendered and visually inspected.
