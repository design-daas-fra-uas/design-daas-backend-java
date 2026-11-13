# Operations guide

> **Audience:** operators and DevOps engineers
> **Last reviewed:** 2026-11-13 · [Documentation index](../README.md#documentation)

This guide covers running the backend in a shared or production environment. Installation and the
full list of environment variables are in the [README](../README.md#installation).

## In short

- Run with the `prod` profile, behind a reverse proxy that provides HTTPS.
- Keep two things safe and backed up: the database and the signing key file `rsa-jwk.json`.
- Monitor `GET /actuator/health`. Every log line carries a request id that links it to the user
  action that caused it.
- Upgrades are automatic: the new version updates the database structure when it starts.

## Contents

- [Installation with the DESIGN installer](#installation-with-the-design-installer)
- [Production checklist](#production-checklist)
- [Secrets and key material](#secrets-and-key-material)
- [Monitoring and logging](#monitoring-and-logging)
- [Backup and restore](#backup-and-restore)
- [Upgrades](#upgrades)
- [Scaling](#scaling)
- [Administrative tasks](#administrative-tasks)
- [Data protection requests](#data-protection-requests)
- [Fixed settings](#fixed-settings)
- [Troubleshooting](#troubleshooting)

## Installation with the DESIGN installer

In a complete DESIGN installation, the installer of the DaaS application (`design-daas`
repository, folder `installer/`) builds and runs this backend. It is called the **auth service**
there. Run the commands from `installer/` as root:

| Command | What it does |
|---|---|
| `make auth-install` | Builds the image from this repository, starts it with its own MariaDB, waits until it answers and tests the admin login |
| `make auth-check` | Tests the running service without changing anything: containers, admin login directly and through nginx, volumes, network isolation |
| `make auth-backup` | Saves the database and the signing key to `installer/auth/backups/` |
| `make auth-admin-password` | Sets the admin password to `ADMIN_PASSWORD` from `daas.env` and revokes the admin's tokens |
| `make auth-status`, `auth-logs`, `auth-restart` | Service control |
| `make auth-reinstall` | Rebuilds from the current code; users and keys are kept |
| `make auth-delete [PURGE=1]` | Removes the service. With `PURGE=1` it also deletes the database and keys; back up first |

Settings come from `installer/daas.env` (`ADMIN_*`, `AUTH_*`, `OAUTH_*`, `MAIL_*`). The installer
publishes port 8080 only on `127.0.0.1`. Browsers reach the service through nginx at
`https://<host>:8443/auth/`. The DaaS application logs in with the admin account, so that account
must stay an admin.

## Production checklist

| # | Item | Why |
|---|---|---|
| 1 | `SPRING_PROFILES_ACTIVE=prod` | Disables Swagger UI and the API description, and the default admin |
| 2 | First start only: `ADMIN_INIT_ENABLED=true` with a strong `ADMIN_PASSWORD` and a real `ADMIN_EMAIL`. Remove both afterwards | Creates the first admin without a well-known password |
| 3 | `OAUTH_ISSUER` set to the public base URL, identical on every instance | Tokens with a different issuer are rejected |
| 4 | `/app/keys` on a persistent, backed-up volume that only the service can read | Holds the private signing key |
| 5 | Dedicated database user, limited to the `design_backend` schema, with a strong password | Least privilege |
| 6 | `CORS_ALLOWED_ORIGINS` set to the exact frontend origin(s) | Prevents other sites from calling the API from a browser |
| 7 | TLS at a reverse proxy; port 8080 not exposed publicly | The backend speaks plain HTTP |
| 8 | `RATE_LIMIT_TRUSTED_PROXIES` set to the proxy's address or CIDR | Otherwise all clients share one rate limit |
| 9 | `LOGGING_LEVEL_DE_FRAUAS_DESIGN_BACKEND=INFO` | The default is `DEBUG` in every profile |
| 10 | `MAIL_ENABLED=true` with a dedicated SMTP account (for Gmail, an app password) | Registration needs the e-mailed code |

## Secrets and key material

| Secret | Storage | Notes |
|---|---|---|
| `DB_PASSWORD`, `MAIL_PASSWORD`, `ADMIN_PASSWORD` | Environment variables or a secret manager | Never commit them; `.env` is git-ignored |
| `rsa-jwk.json` (private signing key) | Volume at `OAUTH_KEYS_DIR` (container default `/app/keys`) | Created on first start with file mode `600`; git-ignored |

**Rotating the signing key:**

1. Stop all instances.
2. Move `rsa-jwk.json` out of the key directory.
3. Start the service. It generates a new key.

All issued tokens become invalid and users must log in again. The decoder holds only one key, so
rotation without downtime is not supported yet.

## Monitoring and logging

### Health

`GET /actuator/health` needs no token. It returns `200 {"status":"UP"}`, or `503` when a
dependency such as the database is unavailable. Use it for container, orchestrator and
load-balancer probes.

### Log format

Logs go to standard output:

```
2026-11-13 10:15:42.118 [http-nio-8080-exec-3] [4f1c…] INFO  d.f.d.b.auth.service.TokenService - passwordGrant — issued token for user=j***@example.org userId=7 scope=user
```

The third field is the **request id**. It comes from the `X-Request-ID` header sent by the
frontend or the DaaS application, or is generated, and it is returned in the response header. To
follow one user action across all DESIGN components, search for this id.

### Log messages worth alerting on

| Message | Meaning |
|---|---|
| `account locked after 5 failed attempts` | Possible brute-force attack on one account |
| `scope escalation attempt` | A client requested a scope above the account's role |
| `Rate limit exceeded` (DEBUG) | A client exceeded a per-IP limit |
| `Unhandled exception` | An unexpected error. The support reference returned to the client is the request id, so searching for it shows the whole request |
| `Async task rejected — queue full` | The mail queue is full; e-mails are being dropped |
| `Token cleanup completed` | Daily cleanup at 03:00; absence indicates a scheduling problem |

## Backup and restore

| Data | Back up? | Notes |
|---|---|---|
| MariaDB schema `design_backend` | **Yes**, regularly (for example with `mariadb-dump --single-transaction`) | Accounts, groups, desktops, permission rules |
| `rsa-jwk.json` | **Yes** | Without it, all issued tokens stop working (users must log in again; no data is lost) |
| `access_tokens`, `refresh_tokens` | Optional | Losing them only ends existing sessions |

To restore: restore the database, restore the key file, and start the service. Flyway detects
the schema version and applies only newer migrations.

## Upgrades

1. Read `CHANGELOG.md` for the target version.
2. Back up the database and the key file.
3. Deploy the new image. Flyway applies pending migrations at startup. If a migration fails, the
   service does not start, and the previous image can be started again against the restored
   backup.

Migration-specific notes:

| Migration | Before upgrading |
|---|---|
| V4 | `desktop_groups.description` must be unique and not null, or the migration fails |
| V5 | Duplicate permission rules are deleted automatically (deny is kept before allow, otherwise the oldest row). Export the rule tables first if you need a record |

## Scaling

The service keeps no session state, so several instances can run behind a load balancer if:

- they share the same signing key and the same `OAUTH_ISSUER`, and
- they use the same database.

Rate limits are held in memory per instance. With *n* instances, a client can make up to *n*
times the configured number of requests. For a strict global limit, rate-limit at the proxy or
gateway.

## Administrative tasks

### Change an admin's password

The API cannot change an admin's password (see risk R-22 in [arc42](arc42.md#111-open-risks-and-technical-debt)).

- **With the DESIGN installer:** set `ADMIN_PASSWORD` in `daas.env`, then run
  `make auth-admin-password`, followed by `make daas-configure daas-restart` so that the DaaS
  application uses the new password.
- **Elsewhere:** create a BCrypt hash with strength 12 and write it to `users.password` for the
  admin, then revoke the admin's tokens:

  ```sql
  UPDATE users SET password = '<bcrypt hash>' WHERE email = 'admin@example.org' AND role = 'admin';
  UPDATE access_tokens  SET revoked = TRUE, revoked_at = NOW(6) WHERE user_id = <id>;
  UPDATE refresh_tokens SET revoked = TRUE, revoked_at = NOW(6) WHERE user_id = <id>;
  ```

Regular users' passwords are changed with `PATCH /user/{id}`, which revokes their tokens
automatically.

### Unlock an account before the 15 minutes are over

There is no API for this. If it is urgent, reset the lock in the database:

```sql
UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE email = 'jane@example.org';
```

### Create the first admin again

If all admins were removed from the database by hand, start the service once with
`ADMIN_INIT_ENABLED=true` and a new `ADMIN_EMAIL` and `ADMIN_PASSWORD`. The account is created only
if no account with that e-mail address exists.

## Data protection requests

| Request | How to handle it |
|---|---|
| Delete a person's data | `DELETE /user/{id}` (or `DELETE /admin/{id}`). This removes the account, its tokens, group memberships and rules, and e-mails a notice |
| Show which data is stored | `GET /user/{id}` returns the name, e-mail address, role, groups and status. Permission rules for the person are listed by `GET /permission_rules` |
| Correct data | `PATCH /user/{id}` |
| Logs | Logs contain masked e-mail addresses and user ids. Delete or rotate them according to the host's log retention policy |

## Fixed settings

These values are set in `application.yml` and normally do not need changing:

| Setting | Value | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `spring.jpa.hibernate.ddl-auto` | `validate` | The application never changes tables itself |
| `spring.jpa.open-in-view` | `false` | Database sessions end with the transaction, not with the HTTP request |
| SMTP timeouts | 5 seconds (connect, read, write) | A slow mail server cannot block the mail threads for long |
| Mail executor | 2–4 threads, queue of 100 | E-mails beyond the queue are dropped with a warning |
| Password hashing | BCrypt, strength 12 | About 0.25 seconds per check on current hardware |
| Account lockout | 5 failures, 15 minutes | Fixed in `AccountLockoutService` |
| Registration code | 8 characters (0–9, A–F), valid 24 hours | Fixed in `UserService` |
| Signing key | RSA 2048 bit, RS256 | Created on first start |
| Log levels | `de.frauas.design.backend`: `DEBUG`; Spring and Hibernate: `INFO`/`WARN` | Override with `LOGGING_LEVEL_<PACKAGE>` |
| `management.health.mail.enabled` | Same as `MAIL_ENABLED` | The health check includes the mail server only when mail is enabled |

## Troubleshooting

| Symptom | Likely cause | Remedy |
|---|---|---|
| Startup fails with `Schema-validation: missing column` | The database schema and the code do not match | Check that the image and the database belong together; check the Flyway history |
| Startup fails with `Validate failed: Migrations have failed validation` | An applied migration file was changed | Restore the original file; add changes as a new migration |
| Every request returns `401` after a redeploy | The key volume was lost, or `OAUTH_ISSUER` changed | Restore the key or the issuer; users log in again |
| All users behind the proxy receive `429` | The proxy is not a trusted proxy | Set `RATE_LIMIT_TRUSTED_PROXIES` |
| No registration e-mails | `MAIL_ENABLED=false` or wrong SMTP settings | Check for `failed to send template` in the log |
| Browser reports a CORS error | The frontend origin is not allowed | Add the exact origin, including scheme and port, to `CORS_ALLOWED_ORIGINS` |
| Login returns `415` | The client sends JSON | Send form-encoded parameters |
| Health returns `503` | The database is unreachable, or, with `MAIL_ENABLED=true`, the mail server | Check `DB_URL` and the database; check `MAIL_HOST`, `MAIL_PORT` and the SMTP credentials |
