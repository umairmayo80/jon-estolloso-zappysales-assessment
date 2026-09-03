# Deployment notes

`docker-compose.yml` at the repository root is intentionally limited to the
local PostgreSQL dependency. It binds the database to `127.0.0.1`, not to all
network interfaces.

`docker-compose.prod.yml` is an example of the production same-origin shape:
the browser reaches one HTTPS origin, Nginx serves the React build, and Nginx
proxies `/api/*` to Spring Boot. The shown Nginx server expects TLS to be
terminated by an ingress, load balancer, or a preceding TLS Nginx server.

Before production deployment:

- Supply database credentials, JWT keys, the refresh-token pepper, and the
  one-time bootstrap administrator through a secret manager.
- Set `APP_JWT_PRIVATE_KEY_FILE` and `APP_JWT_PUBLIC_KEY_FILE` to readable key
  files when using the included Compose example; Compose mounts them read-only
  under `/run/secrets/`, while the backend receives corresponding
  `file:/run/secrets/...` resource paths and requires key material in `prod`.
- Set `APP_COOKIE_SECURE=true` and run the public endpoint only over HTTPS.
- At the proxy trust boundary, overwrite client-supplied forwarded-IP headers.
  The supplied Nginx configuration forwards only its canonical `$remote_addr`
  to Spring so authentication rate limiting cannot be bypassed with a spoofed
  `X-Forwarded-For`. If an ingress sits before Nginx, configure trusted
  real-IP source ranges there before relying on client-IP rate limits.
- Run Liquibase XML changelogs as part of the controlled backend release; do
  not use Hibernate schema creation in production.
- Keep both backend ports private. In production the public API stays on port
  8080, while health and metrics are exposed only on private port 8081 for the
  platform collector; Nginx returns 404 for all `/actuator/*` browser routes.
- Update the pinned PostgreSQL 17 image to the current supported 17.x patch as
  part of normal patch-management policy.

Example validation (after a secret manager or protected deployment environment
has populated `/secure/path/profile-directory.env`):

```bash
docker compose --env-file .env config
docker compose --env-file /secure/path/profile-directory.env \
  -f infra/docker-compose.prod.yml config
```
