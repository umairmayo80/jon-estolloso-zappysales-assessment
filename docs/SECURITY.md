# Security design

## Security objectives

- Only active administrators with the `ADMIN` authority may use the API.
- Browser tokens stay in host-only cookies and are unavailable to JavaScript.
- State-changing requests require a CSRF header in addition to cookie auth.
- Refresh-token theft/reuse is detectable and revokes the affected session
  family.
- Errors and logs are useful for operations without leaking credentials, tokens,
  passwords, or profile data.

## Login and session lifecycle

```text
1. Browser GETs /api/v1/auth/csrf and receives readable XSRF-TOKEN cookie.
2. Browser POSTs login credentials + X-XSRF-TOKEN header.
3. Server validates active local admin, then sets access + refresh cookies.
4. Protected request carries cookies automatically and JWT filter authorizes ADMIN.
5. On access expiry, browser POSTs refresh + current CSRF header.
6. Server rotates the opaque refresh token and issues a new access cookie.
7. Logout, expiry, or detected reuse revokes the refresh session/family and clears cookies.
```

Login failures are deliberately generic. The bootstrap administrator is created
only through explicit one-time configuration, never through a public endpoint.

## Cookie contract

| Cookie | Value | Flags and path | Lifetime |
| --- | --- | --- | --- |
| `PD_ACCESS` | RS256 access JWT | `HttpOnly`, `SameSite=Strict`, host-only, `Path=/api/v1`; `Secure` in prod | 15 minutes |
| `PD_REFRESH` | opaque random refresh token | `HttpOnly`, `SameSite=Strict`, host-only, `Path=/api/v1/auth`; `Secure` in prod | 7-day sliding, 30-day absolute maximum |
| `XSRF-TOKEN` | random CSRF token | readable by the SPA, host-only, `SameSite=Strict`, `Path=/` | session/rotated by server policy |

No cookie receives a `Domain` attribute. The frontend reads only `XSRF-TOKEN`
and sends it as `X-XSRF-TOKEN` for every unsafe request, including login,
refresh, and logout. The access and refresh cookie values are never copied into
local storage, session storage, an Authorization header, application logs, or
test snapshots.

## Token design

Access tokens are signed with RS256 and contain only claims required for
authorization: issuer, subject/admin ID, role, issued time, expiry, and a
non-sensitive token identifier. The server validates signature, issuer,
expiry, and role on every protected request.

Refresh tokens are high-entropy opaque values. PostgreSQL stores only a keyed
digest, session/family identifiers, expiry times, and revocation/reuse metadata.
Every valid refresh atomically invalidates the presented token and creates its
replacement. Presentation of an already-consumed token marks its family
compromised, revokes every member, clears browser cookies, appends an audit
event, and returns an unauthenticated response.

## Authorization and web hardening

- Spring Security uses deny-by-default routing and method-level `ADMIN`
  authorization for application commands.
- Passwords are stored only as BCrypt hashes. Use a generic authentication error
  for unknown/inactive accounts and rate-limit login/refresh endpoints.
- CSRF protection is enabled for all unsafe cookie-authenticated endpoints.
- The production proxy and backend each enforce a 1 MB API body limit. The
  backend counting stream also protects direct and chunked API requests.
- The production proxy uses HTTPS, security headers, a restrictive CSP, and no
  direct browser route to `/actuator/*`; private health/metrics run on port
  8081 rather than the browser-facing API port.
- The proxy overwrites, rather than appends, browser-supplied
  `X-Forwarded-For` values before forwarding requests. This preserves a trusted
  rate-limit key; deployments with an upstream ingress must configure trusted
  real-IP source ranges before using a client address at that boundary.
- Swagger UI and `/v3/api-docs` are enabled only in dev/test by default.
- CORS is absent in the intended same-origin deployment. If a temporary dev
  origin is needed, allow only the explicit Vite origin with credentials; never
  use `*` with cookies.
- Cookie `Secure` is mandatory in production. HSTS belongs at the public HTTPS
  ingress where it can be verified.

## Error, audit, and privacy controls

The global exception layer and Spring Security entry points return RFC 9457
`ProblemDetail` JSON. Responses include a stable application error code and a
request/trace ID, but no stack trace, raw SQL exception, token, cookie, or
password. Field errors name the affected request field without echoing sensitive
values.

Every successful admin login/logout, refresh-reuse detection, and profile or
address mutation writes an append-only audit event with actor, action, target,
time, request ID, and redacted before/after metadata. Audit data is not exposed
in version 1.

## Production checklist

- Generate a separate production RSA key pair and refresh-token pepper; mount
  keys as read-only secrets and rotate them through the deployment process.
  The local dev profile deliberately uses ephemeral key material when no key
  paths are configured; never promote that behavior to production.
- Set production database credentials and bootstrap credentials through a secret
  manager. Remove/bootstrap-disable the one-time password after initial setup.
- Enable `APP_COOKIE_SECURE=true`, TLS, controlled Liquibase releases, backups,
  least-privilege database roles, and log retention policy.
- Restrict the private port 8081 health/metrics collector to the platform
  network, alert on refresh-token reuse and repeated authentication failures,
  and patch PostgreSQL/JDK/base images.
