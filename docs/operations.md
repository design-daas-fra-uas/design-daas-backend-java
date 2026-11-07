[README](../README.md) · [User manual](user-manual.md) · [API](api.md) · [Database](database.md) · [Architecture (arc42)](arc42.md) · [Development](development.md) · [Operations](operations.md)

# Operations / DevOps guide

## Security checklist for production

- [ ] `SPRING_PROFILES_ACTIVE=prod` (turns off Swagger)
- [ ] Bootstrap admin: on the first start only, `ADMIN_INIT_ENABLED=true` plus a strong `ADMIN_PASSWORD`; afterwards remove both (off by default in `prod`)
- [ ] Change the bootstrap admin's password after first login
- [ ] `OAUTH_ISSUER` set to the public base URL (it must match on every instance)
- [ ] `/app/keys` on a persistent, backed-up, access-restricted volume
- [ ] Strong DB password; DB user limited to the `design_backend` schema
- [ ] `CORS_ALLOWED_ORIGINS` set to the exact production frontend origin(s)
- [ ] `RATE_LIMIT_TRUSTED_PROXIES` set to the reverse proxy's address
- [ ] TLS at the proxy; the backend is not exposed directly
- [ ] Log level `INFO` for `de.frauas.design.backend` (`LOGGING_LEVEL_DE_FRAUAS_DESIGN_BACKEND=INFO`)
- [ ] `MAIL_*` configured with an app password or a dedicated SMTP account

## Secrets

| Secret | Where |
|---|---|
| `DB_PASSWORD`, `MAIL_PASSWORD`, `ADMIN_PASSWORD` | Environment or secret manager, never committed (`.env` is git-ignored) |
| `rsa-jwk.json` (**private key**) | Volume at `OAUTH_KEYS_DIR` (git-ignored, mode 600) |

**Rotating the signing key:** stop the app, move `rsa-jwk.json` away, and start the app; a new key
is generated. All existing tokens become invalid and users must log in again. The decoder supports
only one key, so a smooth rotation with two keys at once is not possible yet.

## Backups

- Back up the MariaDB `design_backend` schema regularly (`mariadb-dump`).
- Back up `rsa-jwk.json`. Without it, issued tokens stop working (an inconvenience, not data loss).
- `access_tokens` and `refresh_tokens` can be left out of backups.

## Monitoring and logs

- Logs go to stdout in the format `yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL logger - msg`.
- Useful log lines to search for:
  - `account locked`
  - `scope escalation attempt`
  - `Rate limit exceeded`
  - `Unhandled exception` (with a correlation ID)
  - `Token cleanup completed`
- Health check: `GET /actuator/health` (no token needed) returns `{"status":"UP"}`, or `503` when the database is down.

## Upgrades and migrations

1. Back up the database.
2. Deploy the new image. Flyway applies pending migrations at startup.
3. Before **V4**, make sure `desktop_groups.description` values are unique and not null.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Schema-validation: missing column` at startup | A migration is missing, or the DB is older or newer than the code |
| Every request returns `401 invalid_token` after a redeploy | The key volume was lost, or `OAUTH_ISSUER` changed |
| Users behind the proxy get `429` together | The proxy is not in `RATE_LIMIT_TRUSTED_PROXIES` |
| No registration e-mails | `MAIL_ENABLED=false`, or wrong SMTP credentials (look for `failed to send template` in the log) |
| `415` on login | The request sends JSON; send form-encoded parameters instead |
| CORS error in the browser | The frontend origin is not in `CORS_ALLOWED_ORIGINS` (it must match exactly, including the port) |
