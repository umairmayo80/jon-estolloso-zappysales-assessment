---
title: "Profile Directory"
subtitle: "Assessment for Full Stack Developer"
author: "<FirstName> <LastName>"
date: "2026-09-03"
lang: en-US
geometry: margin=0.72in
fontsize: 10.5pt
colorlinks: true
linkcolor: blue
urlcolor: blue
---
> Before submitting, rename this file to
> `<LastName>_<FirstName>_AssessmentForFullStackDeveloper_<YYYY-MM-DD>.md`,
> replace all angle-bracket placeholders, render the PDF, and visually inspect
> the generated page previews.

# Executive summary

Profile Directory is an administrator-only web application for viewing and
modifying managed user profiles and their one-to-many addresses. The solution
uses a Java 21/Spring Boot API, PostgreSQL 17.6 with Liquibase XML changelogs,
and a React/MUI interface. It is deliberately structured as a modular monolith
with a separately deployable frontend, secure cookie-based session handling,
versioned REST endpoints, and repeatable documentation/test delivery.

# Requirement traceability

| Requirement                                | Delivered approach                                                                                                                                  |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| User list and profile/address modification | React routes provide directory, profile detail, and nested address create/edit flows                                                                |
| One user to multiple addresses             | `user_profiles` owns ordered `addresses`; detail response returns ordered active and archived addresses, with archived rows flagged `deleted` |
| Java/Spring API                            | Java 21, Spring Boot 4 modular MVC application with explicit DTOs                                                                                   |
| React + MUI                                | React, MUI theme, MUI X Data Grid desktop directory, responsive mobile cards                                                                        |
| Clear navigation/state                     | URL-backed directory filters and route-backed profile/edit flows preserve return state                                                              |
| Authentication/security                    | Admin-only JWT access cookie, rotating opaque refresh cookie, CSRF header, audit history                                                            |
| Database migrations                        | Liquibase XML master/child changelogs, PostgreSQL 17.6, Hibernate validation only                                                                   |
| Documentation/submission                   | README, architecture/security/API/testing/UI specifications, OpenAPI, this PDF source                                                               |

# Architecture

```text
Browser (React + MUI)
       |
       | HTTPS, same origin, HttpOnly cookies
       v
Nginx: static SPA + /api reverse proxy
       |
       v
Spring Boot 4 / Java 21
  auth | users | addresses | audit | shared
       |
       v
PostgreSQL 17.6 (Liquibase XML changelogs)
```

The frontend and backend are separate directories and containers. Nginx presents
one browser origin, which avoids permissive cross-origin cookie settings. The
backend uses feature-first modules with controllers/DTOs, application services,
domain models, and repositories separated within each feature.

## Data relationships

```text
admin_accounts 1 --- * refresh_sessions
admin_accounts 1 --- * audit_events (actor)
user_profiles  1 --- * addresses
admin_accounts 1 --- * audit_events (profile/address mutations)
```

Profiles and addresses use UUID public identifiers, version columns, and soft
delete metadata. The profile email remains uniquely reserved after deletion;
restoration is preferred over recreating the same identity. A partial database
constraint permits one active primary address for a profile.

# Authentication and security

```text
CSRF bootstrap -> login -> access JWT + opaque refresh cookie
     -> protected API request -> access expiry -> refresh rotation
     -> reused refresh token -> revoke family + audit + sign in again
```

- Access JWT: RS256, 15 minutes, `HttpOnly`, `SameSite=Strict`, host-only,
  `/api/v1` path; `Secure` in production.
- Refresh token: opaque, server stores only a keyed digest, 7-day sliding and
  30-day absolute lifetime, `HttpOnly`, `SameSite=Strict`, host-only,
  `/api/v1/auth` path.
- CSRF: readable `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header required for
  every unsafe request, including login, refresh, and logout.
- Authorization: active local administrators with `ADMIN` role only; generic
  login errors, auth rate limits, private operations endpoints, redacted logs.

# API and error strategy

The versioned base path is `/api/v1`. Core endpoints are `auth/csrf`,
`auth/login`, `auth/refresh`, `auth/logout`, `auth/me`, and nested
`users/{userId}/addresses` CRUD. User list requests support query, status,
whitelisted sort, zero-based page, and page size capped at 100.

Reads return strong ETags. Update, delete, and restore require `If-Match`;
missing preconditions receive `428`, and stale updates receive `412`. All errors
use RFC 9457 `ProblemDetail` JSON with a stable error code and request ID, never
stack traces or sensitive values.

# UI design and navigation

The selected **Directory Desk** direction is a clean, light B2B workspace with
a fog-white canvas, cobalt active state, Inter typography, restrained borders,
and semantic success/destructive colors. Three static HTML concepts are kept in
`design/mockups`; Directory Desk is the chosen implementation direction.

The login page is focused and minimal. `/users` provides filtering and a full
desktop Data Grid; narrow screens change that dataset into accessible cards.
Detail pages keep the user summary and addresses together. Desktop editing uses
route-backed drawers; mobile editing uses full-screen routes. Visible labels,
inline errors, keyboard focus, 44px touch targets, reduced-motion behavior, and
responsive checks at 390/768/1024/1440px are part of the acceptance criteria.

## Selected mock screenshots

![Directory Desk selected desktop overview: administrator sign-in, people directory, and profile/address record](../../design/previews/directory-desk-desktop.png){ height=7.6in }

# Verification evidence

| Area     | Evidence                                                                                       |
| -------- | ---------------------------------------------------------------------------------------------- |
| Backend  | unit, MVC/security, and Testcontainers/Liquibase integration tests                             |
| Security | login/CSRF/cookie/refresh rotation/reuse/authorization test cases                              |
| Frontend | component, accessibility, form recovery, and responsive browser tests                          |
| Delivery | lint/type/build checks, Docker builds, generated OpenAPI artifact, PDF page-preview inspection |

Local setup begins with `cp .env.example .env` and
`docker compose --env-file .env up -d postgres`. Run the backend with JDK 21,
the frontend with the repository Node version, and use Docker for PostgreSQL and
backend integration-test dependencies. See `README.md` for exact commands.

# Design rationale

The design makes the relationship users already understand explicit: start in a
scan-friendly directory, open one profile, then work with that profile's
addresses in place. URL-backed list state supports return navigation without
losing a search. Soft delete, audit history, CSRF, refresh rotation, and ETags
are pragmatic safeguards for an administrative tool where accidental data loss
or session misuse is more harmful than a little extra implementation structure.

# Submission checklist

- [ ] Replaced author/date/filename placeholders.
- [ ] Included the selected mockup screenshot and checked it contains no real PII.
- [ ] Ran documented backend/frontend checks and recorded any relevant result.
- [ ] Generated the PDF and inspected `output/pdf/previews/` page images.
- [ ] Submitted only the final PDF/project source; excluded `.env` and secrets.
