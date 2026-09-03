# API contract

The runtime OpenAPI document is authoritative. In `dev` and `test` it is
available at `/v3/api-docs`, `/v3/api-docs.yaml`, and Swagger UI. This document
is a concise human guide to the versioned contract.

- Base path: `/api/v1`
- Content type: `application/json`
- Authentication: cookie-based `PD_ACCESS` JWT plus CSRF header for unsafe calls

## Authentication

| Method and path | Purpose | Authentication |
| --- | --- | --- |
| `GET /auth/csrf` | Set/return CSRF token cookie | public |
| `POST /auth/login` | Authenticate a local administrator and set cookies | CSRF header required |
| `POST /auth/refresh` | Rotate refresh session and renew access cookie | refresh cookie + CSRF header |
| `POST /auth/logout` | Revoke the current refresh session and clear cookies | authenticated + CSRF header |
| `GET /auth/me` | Return current administrator summary | ADMIN |

Login request:

```json
{
  "email": "admin@example.test",
  "password": "correct horse battery staple"
}
```

`GET /auth/me` returns the administrator's public ID, normalized email, role,
and display name. Login and refresh return `200 OK` with an `AuthResponse`
containing that non-sensitive administrator summary plus cookie headers;
credentials and token values are never included in JSON. `GET /auth/csrf`
returns `{ "token": "..." }` while also ensuring the `XSRF-TOKEN` cookie exists.

## Users and addresses

All profile/address resources require `ADMIN`. IDs are UUIDs.

| Method and path | Operation | Concurrency |
| --- | --- | --- |
| `GET /users` | Paginated, searchable user directory | n/a |
| `POST /users` | Create a profile | CSRF |
| `GET /users/{userId}` | Profile detail with active and archived addresses | returns profile ETag |
| `PATCH /users/{userId}` | Update profile fields | `If-Match` required |
| `DELETE /users/{userId}` | Soft delete profile | `If-Match` required |
| `POST /users/{userId}/restore` | Restore profile | `If-Match` required |
| `POST /users/{userId}/addresses` | Add address | CSRF |
| `GET /users/{userId}/addresses/{addressId}` | Get one address | returns address ETag |
| `PATCH /users/{userId}/addresses/{addressId}` | Update address | `If-Match` required |
| `DELETE /users/{userId}/addresses/{addressId}` | Soft delete address | `If-Match` required |
| `POST /users/{userId}/addresses/{addressId}/restore` | Restore address | `If-Match` required |

List parameters:

| Parameter | Default | Rules |
| --- | --- | --- |
| `query` | empty | case-insensitive search across email, first name, last name |
| `status` | `active` | `active`, `deleted`, or `all` |
| `sort` | `lastName,asc` | only documented sortable fields/directions are accepted |
| `page` | `0` | zero-based |
| `size` | `20` | maximum `100` |

Example `POST /users` request:

```json
{
  "email": "maria.chen@example.test",
  "firstName": "Maria",
  "lastName": "Chen"
}
```

Example address payload:

```json
{
  "label": "Head office",
  "line1": "42 Market Street",
  "line2": null,
  "city": "Karachi",
  "region": "Sindh",
  "postalCode": "74000",
  "countryCode": "PK",
  "primary": true,
  "displayOrder": 0
}
```

`GET /users` returns a `PageResponse<UserSummary>` containing `content`, `page`,
`size`, `totalElements`, `totalPages`, and the resolved `sort`. `UserDetail`
contains the profile fields, deletion metadata, and its ordered
`AddressResponse` list (including archived addresses, marked `deleted`). The
exact field schemas, requiredness, and examples are published in OpenAPI.

## Optimistic concurrency

Reads emit a strong ETag, for example:

```http
ETag: "user-4e3d2b-v7"
```

Clients send that exact value on update, delete, and restore:

```http
If-Match: "user-4e3d2b-v7"
```

Missing ETags receive `428 Precondition Required`; stale ETags receive
`412 Precondition Failed`. The UI preserves the local draft and asks the
administrator to refresh/reconcile instead of silently overwriting another
administrator's changes.

## Errors

All errors use `application/problem+json` and RFC 9457 `ProblemDetail` shape.

```json
{
  "type": "https://profile-directory.example/problems/validation",
  "title": "Validation failed",
  "status": 422,
  "detail": "Correct the highlighted fields and try again.",
  "instance": "/api/v1/users",
  "code": "VALIDATION_FAILED",
  "traceId": "01J...",
  "fieldErrors": {
    "email": "Enter a valid email address."
  }
}
```

| Status | Stable code class | Meaning |
| --- | --- | --- |
| `400` | `MALFORMED_JSON` or `INVALID_REQUEST` | invalid JSON, type, or query syntax |
| `401` | `UNAUTHENTICATED` or `INVALID_CREDENTIALS` | missing/invalid/expired session or generic login failure |
| `403` | `FORBIDDEN` | insufficient role or CSRF failure |
| `404` | `NOT_FOUND` | unavailable profile/address |
| `409` | `CONFLICT` | duplicate normalized email or domain invariant |
| `412` | `STALE_VERSION` | stale ETag |
| `413` | `PAYLOAD_TOO_LARGE` | request body exceeds the 1 MB API limit |
| `422` | `VALIDATION_FAILED` | valid JSON with invalid fields |
| `428` | `PRECONDITION_REQUIRED` | missing `If-Match` |
| `429` | `RATE_LIMITED` | auth rate limit exceeded |
| `500` | `INTERNAL_ERROR` | unexpected, non-sensitive server error |
