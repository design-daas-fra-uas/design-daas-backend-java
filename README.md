# DESIGN DaaS Backend

[![CI](https://github.com/design-daas-fra-uas/design-daas-backend-java/actions/workflows/ci.yml/badge.svg)](https://github.com/design-daas-fra-uas/design-daas-backend-java/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)

The identity, authorization and inventory service of **DESIGN DaaS** (Desktop as a Service) at
Frankfurt University of Applied Sciences.

In plain words: this service handles logging in, user accounts and groups, the list of available
desktops, and the rules for who may do what. It replaces the earlier PHP backend and keeps the
interface that the frontend and the DaaS application already use, so both keep working.

## Features

- **Authentication:** OAuth2-style password and refresh grants. They issue RS256-signed JWT
  access tokens and single-use refresh tokens that rotate on every refresh. Tokens can be revoked
  on the server at any time.
- **Account management:** self-registration with e-mail verification, plus administration of
  users, admins and user groups.
- **Desktop inventory:** desktops and desktop groups, and which user groups may use them.
- **Permission check:** a fail-closed four-step cascade that answers *"may this user call this
  function?"* for the DaaS application. The rules are maintained through the API.
- **Hardening:** BCrypt password hashes, account lockout, per-IP rate limiting, request-id
  tracing, and error responses that never expose internals.

| | |
|---|---|
| Version | 2.0.0 — see [CHANGELOG](CHANGELOG.md) |
| Runtime | Java 21, Spring Boot 4.1 (Spring Framework 7, Spring Security 7) |
| Database | PostgreSQL, schema managed by Flyway |
| API description | OpenAPI 3 with Swagger UI (springdoc) |
| Quality gates | 19 ArchUnit rules and at least 80 % line and branch coverage, enforced by `mvn verify` |

## Related repositories

| Repository | Role |
|---|---|
| `design-daas` | DaaS application; calls the permission check |
| `design-daas-application-frontend` | Web frontend |
| `design-daas-e2e` | End-to-end tests of the whole system (Playwright) |

## Installing the complete system

When the backend runs in the `local` or `dev` profile, the interactive API description is
available at <http://localhost:8080/swagger-ui.html>.

## Quick start

Requires JDK 21, Maven 3.9+ and Docker.

```bash
# 1. Start PostgreSQL
docker run -d --name design-db -p 5432:5432 \
  -e POSTGRES_DB=design_backend \
  -e POSTGRES_USER=design \
  -e POSTGRES_PASSWORD=design \
  postgres:18

# 2. Start the backend (profile "local")
mvn spring-boot:run
```

On first start, Flyway creates the schema, and a default admin `admin@design.local` with the
password `Admin123!` is created. Try it:

```bash
curl -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=password -d username=admin@design.local -d 'password=Admin123!'
```

> [!WARNING]
> The default admin credentials are for local development only. The `prod` profile does not
> create this account unless `ADMIN_INIT_ENABLED=true`. See the
> [production checklist](docs/operations.md#production-checklist).

## Installation

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin recommended |
| Maven | 3.9 or later | No Maven wrapper is included |
| PostgreSQL | 15 or later | Tested with 18 |
| Docker | Current | Optional: local PostgreSQL, container build, migration test |
| SMTP account | – | Optional; required for registration e-mails (`MAIL_ENABLED=true`) |

### Run from source

1. **Create the database and user** (not needed if you used the Docker command above):

   ```sql
   CREATE ROLE design LOGIN PASSWORD 'design';
   CREATE DATABASE design_backend OWNER design;
   ```

2. **Configure the environment** (optional; the defaults match step 1). Spring Boot does not read
   `.env` files, so export the variables in your shell or IDE:

   ```bash
   cp .env.example .env    # adjust; note that DB_PASSWORD there is "design_password"
   set -a; source .env; set +a
   ```

3. **Build and test.** Unit and integration tests use an in-memory H2 database. The PostgreSQL
   migration test runs only when Docker is available.

   ```bash
   mvn clean verify
   ```

4. **Run:**

   ```bash
   java -jar target/design-daas-backend-2.0.0.jar
   ```

On startup the service:

- applies the Flyway migrations,
- creates the JWT signing key `rsa-jwk.json` in `OAUTH_KEYS_DIR` if it does not exist yet, and
- creates the default admin, if enabled and the e-mail address is not taken.

### Run as a container

```bash
docker build -t design-daas-backend:2.0.0 .

docker run -d --name design-backend -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://<db-host>:5432/design_backend \
  -e DB_USERNAME=design -e DB_PASSWORD='<db-password>' \
  -e OAUTH_ISSUER=https://api.example.org \
  -e FRONTEND_URL=https://app.example.org \
  -e ADMIN_INIT_ENABLED=true -e ADMIN_EMAIL=admin@example.org -e ADMIN_PASSWORD='<strong-password>' \
  -v design-keys:/app/keys \
  design-daas-backend:2.0.0
```

Remove `ADMIN_INIT_ENABLED` after the first start.

> [!IMPORTANT]
> Keep `/app/keys` on a persistent volume. If the signing key is lost, every issued token becomes
> invalid. All instances must share the same key.

<details>
<summary>Example <code>docker-compose.yml</code> for local development</summary>

```yaml
services:
  db:
    image: postgres:18
    environment:
      POSTGRES_DB: design_backend
      POSTGRES_USER: design
      POSTGRES_PASSWORD: design
    ports: ["5432:5432"]
    volumes: [db-data:/var/lib/postgresql]
  backend:
    build: .
    depends_on: [db]
    ports: ["8080:8080"]
    environment:
      DB_URL: jdbc:postgresql://db:5432/design_backend
      DB_USERNAME: design
      DB_PASSWORD: design
      SPRING_PROFILES_ACTIVE: dev
    volumes: [keys:/app/keys]
volumes:
  db-data:
  keys:
```

</details>

## Configuration

All settings are in `src/main/resources/application.yml` and can be overridden with environment
variables.

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local`, `dev` or `prod` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/design_backend` | JDBC URL |
| `DB_USERNAME`, `DB_PASSWORD` | `design`, `design` | Database credentials |
| `OAUTH_ISSUER` | `http://localhost:8080` | `iss` claim of issued tokens; checked on every request |
| `OAUTH_KEYS_DIR` | `./keys` | Directory of the signing key `rsa-jwk.json` |
| `OAUTH_ACCESS_TOKEN_TTL_SECONDS` | `3600` | Access-token lifetime |
| `OAUTH_REFRESH_TOKEN_TTL_DAYS` | `30` | Refresh-token lifetime |
| `ADMIN_INIT_ENABLED` | `true` (`false` in `prod`) | Create the default admin at startup |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | `admin@design.local`, `Admin123!` | Default admin credentials. **Change them** |
| `MAIL_ENABLED` | `false` | Send e-mails. If `false`, registration codes are only logged at `DEBUG` |
| `MAIL_HOST`, `MAIL_PORT` | `smtp.gmail.com`, `587` | SMTP server (STARTTLS) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | empty | SMTP credentials |
| `MAIL_FROM` | `noreply@design.local` | Sender address; also receives application-access requests |
| `FRONTEND_URL` | `http://localhost:3000` | Frontend origin |
| `CORS_ALLOWED_ORIGINS` | value of `FRONTEND_URL` | Comma-separated list of exact origins |
| `RATE_LIMITING_ENABLED` | `true` | Per-IP rate limiting on the public endpoints |
| `RATE_LIMIT_AUTH_TOKEN` | `20` | Requests per minute on `POST /oauth2/user/token` |
| `RATE_LIMIT_REGISTER` | `5` | Requests per minute on `POST /user` |
| `RATE_LIMIT_VALIDATE_EMAIL` | `10` | Requests per minute on `POST /user/validate_email` |
| `RATE_LIMIT_RESEND_CODE` | `3` | Requests per minute on `POST /user/resend_code` |
| `RATE_LIMIT_TRUSTED_PROXIES` | `127.0.0.1/32,::1/128` | Proxies whose `X-Forwarded-For` header is trusted |

Properties without a dedicated environment variable. Set them in a profile file or on the command
line, for example `--app.token.cleanup.cron="0 0 4 * * *"`:

| Property | Default | Description |
|---|---|---|
| `app.token.cleanup.cron` | `0 0 3 * * *` | Schedule of the expired-token cleanup (JVM time zone) |
| `app.rate-limiting.eviction-interval-ms` | `300000` | How often idle rate-limit buckets are removed |
| `app.security.swagger-public` | `false` (`true` in `local` and `dev`) | Serve Swagger UI without authentication |

### Profiles

| Profile | Behaviour |
|---|---|
| `local` (default), `dev` | Swagger UI and `/v3/api-docs` are public |
| `prod` | Swagger UI and the API description are disabled; the default admin is created only when `ADMIN_INIT_ENABLED=true` |
| `test` | Used by the test suite: H2, Flyway off, rate limiting off |

## Security

- Passwords are stored only as BCrypt hashes. Tokens are signed with a private RSA key that the
  service creates on first start; keep it secret and back it up.
- Every request is checked against the database, so logout, disabling and password changes take
  effect immediately.

See [Security in arc42](docs/arc42.md#81-security) for the threat overview and
[Data protection](docs/arc42.md#86-data-protection) for the personal data that is stored.

## Contributing

Read the [Developer guide](docs/development.md) before opening a pull request. A change is
complete when `mvn verify` passes and the code, tests, documentation and `CHANGELOG.md` are
updated together (see the [definition of done](docs/development.md#definition-of-done)).

## Documentation

| Document | Audience | Contents |
|---|---|---|
| [User manual](docs/user-manual.md) | Users, admins, frontend developers | Registration, login, sessions, administration tasks |
| [API reference](docs/api.md) | Frontend and DaaS-application developers | Every endpoint with access level, request, response and errors |
| [Database](docs/database.md) | Developers, database administrators | Schema, ER diagram, migrations, permission resolution |
| [Architecture (arc42)](docs/arc42.md) | Architects, developers | Goals, context, building blocks, decisions, quality, risks |
| [Developer guide](docs/development.md) | Developers | Project layout, build, tests, conventions, definition of done |
| [Operations guide](docs/operations.md) | Operators, DevOps | Production checklist, secrets, monitoring, backups, upgrades |

When the backend runs in the `local` or `dev` profile, the interactive API description is
available at <http://localhost:8080/swagger-ui.html>.