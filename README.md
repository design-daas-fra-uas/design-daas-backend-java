# Design DaaS Backend (Java)

Spring Boot backend for **DESIGN DaaS** (Desktop-as-a-Service) at Frankfurt UAS. It replaces the
earlier PHP backend and provides:

- **Authentication**: login with e-mail and password, which returns a signed JWT access token
  plus a refresh token, rotated on every refresh, with logout and server-side revocation.
- **User management**: self-service registration with e-mail verification, plus admin-managed
  users, admins and user groups.
- **Desktop management**: desktops and desktop groups, and which user groups may use which
  desktop groups.
- **Permission checks**: a 4-step allow/deny cascade that answers "may user X call function Y?"
  for the DESIGN DaaS platform.

| | |
|---|---|
| Version | 2.0.0 (`pom.xml`) |
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7, Spring Security 7) |
| Database | MariaDB (schema managed by Flyway) |
| API docs | OpenAPI 3 / Swagger UI (springdoc 3) |
| Build | Maven 3.9+ |

---

## Documentation

| Document | For | Contents |
|---|---|---|
| [User manual](docs/user-manual.md) | Users, admins, frontend devs | How to use the API as a user or admin: registration, login, tokens and the typical admin workflow. |
| [API reference](docs/api.md) | Frontend and platform devs | Every endpoint with access rules, request bodies and status codes. |
| [Database](docs/database.md) | Developers, DBAs | Schema, ER diagram, migrations and how permissions are resolved. |
| [Architecture (arc42)](docs/arc42.md) | Architects, developers | Architecture documentation following the arc42 template. |
| [Developer guide](docs/development.md) | Developers | Project layout, commands, coding and documentation conventions. |
| [Operations guide](docs/operations.md) | DevOps, operators | Deployment checklist, secrets, backups, monitoring and troubleshooting. |

Interactive API documentation is served at `/swagger-ui.html` in the `local` and `dev` profiles.

---

## Quick start

```bash
# 1. Start a MariaDB instance
docker run -d --name design-db -p 3306:3306 \
  -e MARIADB_DATABASE=design_backend \
  -e MARIADB_USER=design \
  -e MARIADB_PASSWORD=design \
  -e MARIADB_ROOT_PASSWORD=root \
  mariadb:11

# 2. Run the backend (profile "local" is the default)
mvn spring-boot:run

# 3. Log in as the bootstrap admin
curl -s -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=password \
  -d username=admin@design.local \
  -d 'password=Admin123!'

# 4. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

> ⚠️ `admin@design.local` / `Admin123!` is a **well-known default** for development only.
> The `prod` profile does not create this account unless `ADMIN_INIT_ENABLED=true`. Set
> `ADMIN_EMAIL` and `ADMIN_PASSWORD` in every shared environment. See the [security checklist](docs/operations.md#security-checklist-for-production).

---

## Installation

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin recommended |
| Maven | 3.9+ | The repository has **no Maven wrapper** (`.mvn/` is git-ignored), so install Maven yourself |
| MariaDB | 10.6+ / 11.x | MySQL may work via the MariaDB driver but is not tested |
| Docker | optional | For the container build and for running MariaDB locally |
| SMTP account | optional | Only needed when `MAIL_ENABLED=true` |

### Run locally with Maven

1. **Create the database and user** (skip this if you used the Docker command in Quick start):

   ```sql
   CREATE DATABASE design_backend CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'design'@'%' IDENTIFIED BY 'design';
   GRANT ALL PRIVILEGES ON design_backend.* TO 'design'@'%';
   ```

2. **Configure the environment.** Copy `.env.example` to `.env` and adjust it. Spring Boot does
   **not** read `.env` files on its own, so export the variables in your shell or IDE run
   configuration:

   ```bash
   cp .env.example .env
   set -a; source .env; set +a
   ```

3. **Build and test:**

   ```bash
   mvn clean verify
   ```

   Tests use an in-memory H2 database, so the build needs no MariaDB.

4. **Run:**

   ```bash
   mvn spring-boot:run
   # or
   java -jar target/design-daas-backend-2.0.0.jar
   ```

   On first start the application:
   - runs the Flyway migrations `V1`–`V4` against the database,
   - generates an RSA-2048 signing key at `${OAUTH_KEYS_DIR}/rsa-jwk.json` (default `./keys`),
     with file mode 600 on POSIX systems,
   - creates the default admin account if `ADMIN_INIT_ENABLED` is true (the default outside
     `prod`) and the e-mail does not exist yet.

### Run with Docker

The `Dockerfile` is a two-stage build: Maven builds the jar, then the jar runs on a JRE 21 Alpine
image.

```bash
docker build -t design-daas-backend:2.0.0 .

docker run -d --name design-backend -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:mariadb://design-db:3306/design_backend \
  -e DB_USERNAME=design -e DB_PASSWORD=design \
  -e OAUTH_ISSUER=https://api.example.org \
  -e ADMIN_INIT_ENABLED=true -e ADMIN_PASSWORD='<strong-secret>' \
  -e FRONTEND_URL=https://app.example.org \
  -v design-keys:/app/keys \
  design-daas-backend:2.0.0
```

> In `prod` the bootstrap admin is only created when `ADMIN_INIT_ENABLED=true`. Enable it for the
> first start with a strong `ADMIN_PASSWORD`, then remove the variable.

> **Mount `/app/keys` on a persistent volume.** If the signing key is lost (for example, the
> container is recreated without a volume), every issued token becomes invalid and all users
> must log in again. Every replica must use the **same** key file.

A minimal `docker-compose.yml` for local development (not part of the repository):

```yaml
services:
  db:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: design_backend
      MARIADB_USER: design
      MARIADB_PASSWORD: design
      MARIADB_ROOT_PASSWORD: root
    ports: ["3306:3306"]
    volumes: [db-data:/var/lib/mysql]
  backend:
    build: .
    depends_on: [db]
    ports: ["8080:8080"]
    environment:
      DB_URL: jdbc:mariadb://db:3306/design_backend
      DB_USERNAME: design
      DB_PASSWORD: design
      SPRING_PROFILES_ACTIVE: dev
    volumes: [keys:/app/keys]
volumes:
  db-data:
  keys:
```

### Configuration reference

All settings live in `src/main/resources/application.yml` and can be overridden with environment
variables.

| Env variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local`, `dev` or `prod` (see below) |
| `DB_URL` | `jdbc:mariadb://localhost:3306/design_backend` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `design` / `design` | DB credentials |
| `OAUTH_KEYS_DIR` | `./keys` | Directory holding `rsa-jwk.json` (the private signing key) |
| `OAUTH_ISSUER` | `http://localhost:8080` | `iss` claim written into JWTs and **checked** on every request |
| `OAUTH_ACCESS_TOKEN_TTL_SECONDS` | `3600` | Access token lifetime |
| `OAUTH_REFRESH_TOKEN_TTL_DAYS` | `30` | Refresh token lifetime |
| `ADMIN_INIT_ENABLED` | `true` (`false` in `prod`) | Create the bootstrap admin at startup if the e-mail does not exist |
| `ADMIN_EMAIL` | `admin@design.local` | Bootstrap admin e-mail |
| `ADMIN_PASSWORD` | `Admin123!` | Bootstrap admin password, **change it** |
| `MAIL_ENABLED` | `false` | If false, no mail is sent (codes are only logged at DEBUG) |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | SMTP server |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | SMTP credentials (for Gmail, an App Password) |
| `MAIL_FROM` | `noreply@design.local` | Sender, and recipient of application-access request mails |
| `FRONTEND_URL` | `http://localhost:3000` | Frontend origin |
| `CORS_ALLOWED_ORIGINS` | `${FRONTEND_URL}` | Comma-separated exact origins |
| `RATE_LIMITING_ENABLED` | `true` | IP-based rate limiting on public endpoints |
| `RATE_LIMIT_AUTH_TOKEN` | `20` | req/min/IP on `POST /oauth2/user/token` |
| `RATE_LIMIT_REGISTER` | `5` | req/min/IP on `POST /user` |
| `RATE_LIMIT_VALIDATE_EMAIL` | `10` | req/min/IP on `POST /user/validate_email` |
| `RATE_LIMIT_RESEND_CODE` | `3` | req/min/IP on `POST /user/resend_code` |
| `RATE_LIMIT_TRUSTED_PROXIES` | `127.0.0.1/32,::1/128` | CIDRs whose `X-Forwarded-For` header is trusted |

Other properties that have no environment variable:

| Property | Default | Purpose |
|---|---|---|
| `app.token.cleanup.cron` | `0 0 3 * * *` | When expired tokens are purged (JVM time zone) |
| `app.security.swagger-public` | `false` (`true` in `local`/`dev`) | Swagger UI without authentication |

### Spring profiles

| Profile | Effect |
|---|---|
| `local` (default) | Swagger UI and `/v3/api-docs` are public |
| `dev` | Same as `local` |
| `prod` | Swagger UI and API docs are **disabled** entirely; the bootstrap admin is **not** created unless `ADMIN_INIT_ENABLED=true` |
| `test` (tests only) | H2 in MySQL mode, Flyway off, `ddl-auto=create-drop`, rate limiting off, keys in `./test-keys` |
