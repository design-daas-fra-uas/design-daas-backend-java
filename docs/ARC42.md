# Design DaaS Backend — Architecture Documentation (arc42)

> Follows the [arc42](https://arc42.org) template (12 sections). Scope: the
> `design-daas-backend` Java/Spring Boot service only.

## 1. Introduction and Goals

### 1.1 Background

This repository is the subject of a Master thesis whose task description is
kept at `docs/Ausschreibung_DaaS_Backend_DESIGN_2026.pdf`. Per that brief,
the overall **DESIGN** Desktop-as-a-Service system consists of three
separately-repositories parts:

- **DaaS-Anwendung** (the actual desktop-as-a-service application/engine) — Python
- **Frontend** — React
- **Backend** — originally written in **PHP**, called out in the brief as
  qualitatively weak and poorly documented

The thesis goal is to design and implement a **new backend** for DESIGN in
Java. This repository (`design-daas-backend`, Java/Spring Boot) is that
reimplementation: `OpenApiConfig` literally describes it as "Spring Boot
backend replacing the PHP backend", and several classes/tests explicitly
document that they mirror or must stay behaviorally compatible with specific
parts of the old PHP backend (e.g. `PermissionService` ports PHP's
`PermissionCalculationService`, `AccessTokenEntity` "mirrors the PHP
`oauth2_access_token` table", `UserService.requestApplication()` "mirrors PHP
`UserService::requestApplication()`").

### 1.2 Requirements overview

Based on what the code itself implements, this backend provides four
groups of capabilities, consumed by the Frontend and/or the DaaS-Anwendung
over REST:

1. **Authentication** — issue/validate OAuth2-flavored `password` and
   `refresh_token` grants as RS256-signed JWT access tokens (`POST /oauth2/user/token`), plus logout
   (`POST /oauth2/user/logout`)
   and session introspection (`GET /oauth2/user/session`).
2. **Authorization** — answer "is user X allowed to call function Y?" via a
   4-level, fail-closed rule cascade (`GET /permissions/{functionName}/{userId}`,
   `POST /permissions_info`), preserving the old PHP backend's permission
   semantics so the rest of the DESIGN system keeps working unchanged.
3. **User administration** — self-registration with email verification (`POST /user`, `/user/validate_email`,
   `/user/resend_code`),
   admin-managed users/admins/user-groups, enable/disable accounts, and
   an "application request" notification email.
4. **Desktop inventory** — CRUD for virtual desktops and desktop-groups (`/desktop`, `/desktops`, `/desktop_group`,
   `/desktop_groups`) and their
   association to user groups.

The service is **API-only** (aside from Swagger UI). Nothing in the code
indicates it provisions virtual-desktop infrastructure itself — it only
exposes inventory/administrative data and the auth/permission oracle used
by the rest of the DESIGN system.

### 1.3 Quality goals (top 5, informal priority order)

| # | Quality goal                                          | Motivation                                                                                                                                           |
|---|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Security**                                          | Handles credentials, JWTs, and per-function access control for the whole DESIGN system.                                                              |
| 2 | **Correctness of the permission cascade**             | A wrong allow/deny decision is either a security hole or an outage for the Frontend/DaaS-Anwendung.                                                  |
| 3 | **Maintainability**                                   | Enforced layering (ArchUnit), consistent per-module structure, 70% line coverage gate.                                                               |
| 4 | **Operability**                                       | Stateless JWT auth allows horizontal scaling; Flyway-managed schema; Actuator health checks.                                                         |
| 5 | **Behavioral compatibility with the old PHP backend** | Several components are explicit ports of (or are documented as mirroring) specific PHP classes/tables, so other DESIGN parts keep working unchanged. |

### 1.4 Stakeholders

| Role                                   | Expectation                                                                                                |
|----------------------------------------|------------------------------------------------------------------------------------------------------------|
| Frontend (React, separate repo)        | Stable auth/user/desktop REST contract; CORS-allowed origin configured via `app.frontend.url`              |
| DaaS-Anwendung (Python, separate repo) | Stable permission-check contract (`/permissions/**`, `/permissions_info`) using design-daas function names |
| Platform/DaaS administrators           | Manage users, groups, desktops, and access rules via Swagger/API (`SCOPE_admin`)                           |
| End users (desktop consumers)          | Register, verify email, log in, request application access                                                 |
| Thesis author / backend developers     | Clear module boundaries, enforced architecture, fast feedback via tests                                    |

## 2. Constraints

### 2.1 Technical constraints

- **Java 21**, **Spring Boot 4.1.0** (Spring Framework 7 / Spring Security 7.1).
- **MariaDB** as the only supported production datastore; **H2** in-memory for tests.
- **Flyway** is the single source of truth for schema; Hibernate runs in
  `ddl-auto: validate` mode only — it never auto-generates DDL.
- Must preserve the behavior of specific pieces of the **old PHP backend**
  it replaces, since other DESIGN components (Frontend, DaaS-Anwendung)
  are not part of this repository and are not being changed as part of
  this work. Concretely documented in the code: the `endpoint_user_access`
  style permission cascade ports PHP's `PermissionCalculationService`; the
  `access_tokens` table "mirrors the PHP `oauth2_access_token` table"; JSON
  field names (snake_case token response, `permissions_info` shape) follow
  what "the frontend and other API consumers" expect; `UserService
  .requestApplication()` mirrors PHP's `UserService::requestApplication()`.
- RSA keypair for JWT signing is generated on first boot and persisted to
  disk (`OAUTH_KEYS_DIR`); losing this file invalidates all previously
  issued tokens.

### 2.2 Organizational constraints

- Single Maven module, single deployable JAR/Docker image.
- `./mvnw test` / `mvn verify` must pass, including ArchUnit layering rules
  and a 70% JaCoCo line-coverage gate, before merging changes.

## 3. Context and Scope

### 3.1 Business context

The DESIGN system (per the thesis brief) is composed of three parts; this
repository implements only the **Backend** box below, as a replacement for
the old PHP backend:

```
      ┌───────────────┐                     ┌─────────────────────┐
      │   Frontend    │                     │   DaaS-Anwendung    │
      │   (React)     │                     │   (Python)          │
      └───────┬───────┘                     └──────────┬──────────┘
              │ REST (JSON / form-urlencoded)          │ REST — permission
              │ auth, user, desktop endpoints          │ checks using
              ▼                                        │ design-daas
      ┌──────────────────────────────────────────┐     │ function names
      │        design-daas-backend (this repo)   │◀────┘
      │        — Java/Spring Boot                │
      │        replaces the old PHP backend      │◀───── Admin
      └───────────┬──────────────┬───────────────┘       (Swagger UI / REST, SCOPE_admin)
                  ▼              ▼
          ┌──────────────┐  ┌────────────────┐
          │   MariaDB    │  │  SMTP server   │
          │ (users,      │  │ (registration/ │
          │ permissions, │  │  verification  │
          │ desktops)    │  │  emails)       │
          └──────────────┘  └────────────────┘
```

| Neighbor                | Direction | Purpose                                                                                                                                                                                                                                      |
|-------------------------|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Frontend (React)        | inbound   | Calls auth (`/oauth2/user/token`, `/oauth2/user/session`, `/oauth2/user/logout`), user (`/user/**`), and desktop (`/desktop/**`) endpoints on behalf of end users and admins; `app.frontend.url` also drives the default CORS-allowed origin |
| DaaS-Anwendung (Python) | inbound   | Calls the permission-check endpoints (`GET /permissions/{functionName}/{userId}`, `POST /permissions_info`) to decide whether a user may invoke a given design-daas function                                                                 |
| MariaDB                 | outbound  | System of record for users, groups, permissions, desktops, tokens                                                                                                                                                                            |
| SMTP server             | outbound  | Sends HTML registration/verification/application-request emails (Thymeleaf templates); optional, gated by `MAIL_ENABLED`                                                                                                                     |
| Admin / Swagger UI      | inbound   | Interactive API exploration, gated behind `SCOPE_admin` unless `app.security.swagger-public=true`                                                                                                                                            |

> Note: nothing in this repository's code directly references the
> DaaS-Anwendung or Frontend repositories by name/URL — their existence and
> role is established by the thesis brief (PDF), while the concrete
> endpoints/contract above are read directly from this repo's controllers.

### 3.2 Technical context

Single Spring Boot process exposing REST over HTTP on port **8080**.
Stateless: all session state lives in the database (`access_tokens`,
`refresh_tokens`) or the signed JWT itself, so multiple instances can run
behind a load balancer without sticky sessions.

## 4. Solution Strategy

- **Rewrite the old PHP backend as a Java/Spring Boot service** while
  deliberately porting specific behaviorally-critical pieces 1:1 (the
  permission cascade, the access-token table shape, the
  `requestApplication` flow, snake_case JSON field names) so the
  Frontend and DaaS-Anwendung — which are out of scope for this thesis —
  keep working against the new backend unchanged.
- **Stateless JWT auth** (custom RS256 issuance + Spring OAuth2 Resource
  Server validation) instead of a full Spring Authorization Server, since
  only two grant types (`password`, `refresh_token`) are needed and
  `POST /oauth2/user/token` accepts user credentials directly via form
  parameters, as the OAuth2 resource-owner-password-credentials grant does.
- **Database-backed revocation** (`access_tokens`/`refresh_tokens` tables
  with `revoked`/`revoked_at`) so tokens can be invalidated server-side (logout, admin-forced invalidation) despite JWTs
  being normally
  self-contained/stateless.
- **4-level fail-closed permission cascade**, documented in code as ported
  from the old PHP backend's `PermissionCalculationService`, to avoid
  behavior drift for the DaaS-Anwendung's permission checks.
- **One Java package per bounded domain** (`auth`, `permission`, `user`,
  `desktop`) each following an identical `controller → service →
  repository → model` layering, with **ArchUnit tests enforcing the
  layering and dependency direction** at build time so the structure can't
  silently erode.
- **Flyway-only schema management** — Hibernate never writes DDL — to keep
  schema changes explicit, reviewable, and reproducible across
  dev/test/prod.

## 5. Building Block View

### 5.1 Whitebox: overall system

```
de.frauas.design.backend
├── auth         — token issuance/validation, sessions, lockout, cleanup
├── permission   — per-endpoint/per-function access-control cascade
├── user         — users, admins, user-groups, registration, mail
├── desktop      — desktops, desktop-groups, desktop↔group associations
├── bootstrap    — application-startup tasks (default admin creation)
├── config       — Spring @Configuration + typed @ConfigurationProperties
└── shared       — cross-cutting code (DTOs, exceptions, security, utils)
```

Every domain package follows the same internal layering:
`controller → service → repository → model`, plus local `dto` and
`exception` sub-packages. This is enforced by
`src/test/java/.../arch/ArchitectureTest.java` (ArchUnit), which checks:

- **Layer isolation**: controllers are never called by other layers;
  services are only reachable from controllers/config/bootstrap; models are
  only reachable from services/repositories/config/DTOs.
- **Dependency direction**: services never depend on controllers;
  repositories never depend on services; DTOs never depend on
  services/controllers/repositories; `shared` never depends on any feature
  module (it is a dependency leaf).
- **Naming/location conventions**: `@Service` → `*Service`/`*ServiceImpl` in
  a `service` package; `@Repository` → `*Repository` in a `repository`
  package; `@RestController` → `*Controller` in a `controller` package.
- **Coding rules**: constructor injection only (no `@Autowired` fields in
  services), no `java.util.logging`/`System.out`/Joda-Time, `@Transactional`
  only on services/repositories (never controllers).

### 5.2 Module: `auth`

Issues/validates JWT access tokens and opaque refresh tokens; owns login
lockout and token lifecycle.

| Component                                             | Responsibility                                                                                                                                        |
|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TokenController`                                     | `POST /oauth2/user/token` (password/refresh_token grants), `POST /oauth2/user/logout`                                                                 |
| `OAuth2SessionController`                             | Session/token introspection endpoints                                                                                                                 |
| `TokenService`                                        | Grant-type dispatch, credential verification, scope validation — framework-agnostic business logic                                                    |
| `TokenIssuer`                                         | Builds RS256 JWT access tokens (with `email`/`name`/`scope` claims, unique `jti`) and random 256-bit hex refresh tokens; persists both for revocation |
| `TokenRevocationService` / `TokenRevocationValidator` | Revokes tokens on logout; checks revocation status on each authenticated request                                                                      |
| `AccountLockoutService`                               | Tracks `failed_login_attempts`/`locked_until` on users, locks accounts after repeated failures                                                        |
| `JwtUserResolver` / `ScopeResolver`                   | Extracts the authenticated user id from a `Jwt` principal; resolves default scope from user role                                                      |
| `TokenCleanupService`                                 | Scheduled purge of expired/revoked tokens                                                                                                             |
| `AccessTokenEntity` / `RefreshTokenEntity`            | JPA entities backing `access_tokens` / `refresh_tokens`                                                                                               |

### 5.3 Module: `permission`

Resolves "is user X allowed to call function Y", consumed by the
DaaS-Anwendung.

| Component                                                                                                  | Responsibility                                                                                                                                                                                        |
|------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PermissionController`                                                                                     | `GET /permissions/{functionName}/{userId}` (admin-only, checks an arbitrary user); `POST /permissions_info` (any authenticated caller, resolves the user from a body `token` or the caller's own JWT) |
| `PermissionService`                                                                                        | 4-step cascade (see §8 "Architecture Decisions")                                                                                                                                                      |
| `Endpoint` / `EndpointGroup`                                                                               | Represent a design-daas "function" and named groups of functions                                                                                                                                      |
| `EndpointUserAccess`, `EndpointUserGroupAccess`, `EndpointGroupUserAccess`, `EndpointGroupUserGroupAccess` | The four join-entities backing each cascade step, each carrying an `allow_access` boolean                                                                                                             |

### 5.4 Module: `user`

User/admin/user-group lifecycle, registration, and email verification.

| Component                                           | Responsibility                                                                                                                                                                                                               |
|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `UserController`                                    | Self-registration (`POST /user`, public), `/user/validate_email`, `/user/resend_code` (both public), `GET /users`, `PATCH /user/{userId}` (+`/enable`, `/disable`), `POST /user/request-application/{application}/{isoCode}` |
| `AdminController`                                   | `GET /admins`, `POST /admin` (`SCOPE_admin`)                                                                                                                                                                                 |
| `UserGroupController`                               | `GET /user_groups`, `POST /user_group`, `PATCH /user_group/{id}`                                                                                                                                                             |
| `UserService` / `AdminService` / `UserGroupService` | Business logic per sub-domain                                                                                                                                                                                                |
| `AccountValidator`                                  | Registration-code and password-policy validation                                                                                                                                                                             |
| `MailService`                                       | Sends Thymeleaf-rendered HTML registration/verification emails via `spring-boot-starter-mail`                                                                                                                                |
| `BaseUser` (abstract) → `User`, `Admin`             | Single-table-ish user hierarchy; `role` discriminates scope                                                                                                                                                                  |
| `UserGroup`                                         | Named group; backs the `permission` cascade's group-level rules and the `desktop` module's group↔desktop-group links                                                                                                         |

### 5.5 Module: `desktop`

Inventory of virtual desktops and desktop-groups (not provisioning).

| Component                  | Responsibility                                                                                    |
|----------------------------|---------------------------------------------------------------------------------------------------|
| `DesktopController`        | `GET /desktops`, `POST /desktop`, `GET /desktop/{id}`                                             |
| `DesktopGroupController`   | `GET /desktop_groups`, `POST /desktop_group`, `POST /desktop_group/{id}/user_group/{userGroupId}` |
| `DesktopService`           | Business logic, including user-group ↔ desktop-group association rules                            |
| `Desktop` / `DesktopGroup` | JPA entities; a desktop belongs to N desktop-groups, a desktop-group is linked to N user-groups   |

### 5.6 Module: `shared`

Cross-cutting code with **no** dependency on any feature module (enforced
by ArchUnit): `GlobalExceptionHandler`, `ErrorResponse` DTO,
`UserSessionsInvalidatedEvent`, `Authorities` (scope constants),
`RateLimitingFilter` + `TokenBucket` + `RateLimiter` (per-route rate
limiting), `ClientIpResolver` (trusted-proxy-aware client IP extraction),
`LogMasking` / `PasswordPolicy` utilities.

### 5.7 Module: `config` / `bootstrap`

- `SecurityConfig` — stateless JWT resource-server filter chain; only a
  small allow-list of endpoints (`POST /user`, `/user/validate_email`,
  `/user/resend_code`, `/oauth2/user/token`, `GET /version`, optionally
  Swagger) bypass authentication; everything else requires a valid JWT.
- `AuthorizationServerConfig`, `CorsConfig`, `OpenApiConfig`, `PasswordConfig`,
  `AsyncConfig`, `ClockConfig` — supporting Spring `@Configuration` classes.
- `*Properties` classes (`OAuth2Properties`, `SecurityProperties`,
  `CorsProperties`, `RateLimitingProperties`, `AdminInitializerProperties`)
  — typed, validated `@ConfigurationProperties` bound from `app.*` in
  `application.yml`.
- `DataInitializer` (bootstrap) — creates a default admin account (`ADMIN_EMAIL`/`ADMIN_PASSWORD`) on first boot if
  `app.admin-initializer.enabled=true`.

## 6. Runtime View

### 6.1 Login (password grant) + a subsequent permission check

1. Frontend -> `POST /oauth2/user/token` with `grant_type=password` plus
   `username`/`password` form parameters (public endpoint).
2. `TokenController` builds a `GrantRequest`, delegates to `TokenService`.
3. `TokenService` verifies credentials against `UserRepository` (BCrypt,
   via `PasswordConfig`), consults `AccountLockoutService` (rejects if
   locked/disabled), resolves scope via `ScopeResolver`.
4. On success, `TokenIssuer` issues an RS256 JWT access token (persisted in
   `access_tokens`) and a random refresh token (persisted in
   `refresh_tokens`); both returned as a standard OAuth2 token response.
5. DaaS-Anwendung later calls `GET /permissions/{functionName}/{userId}`
   (admin-scoped) or `POST /permissions_info` (any authenticated caller,
   optionally supplying a raw `token` in the body) with the bearer token
   in the `Authorization` header.
6. `SecurityConfig`'s resource-server filter chain validates the JWT
   signature/expiry via `JwtDecoder`, then `TokenRevocationValidator`
   checks the `jti` hasn't been revoked.
7. `PermissionController` -> `PermissionService.checkPermission(userId,
   functionName)` runs the 4-step cascade (section 8) and returns an
   allow/deny `PermissionResultDto`.

### 6.2 Refresh-token grant

`POST /oauth2/user/token` with `grant_type=refresh_token&refresh_token=...`
→ `TokenService` looks up the refresh token, checks `revoked`/`expires_at`,
re-resolves the user, issues a new access token (and, depending on
rotation policy, a new refresh token), all via the same `TokenIssuer`.

### 6.3 Logout

`POST /oauth2/user/logout` (authenticated) → `JwtUserResolver` extracts the
caller's user id from the JWT principal → `TokenRevocationService.logout`
marks the current access token's `jti` and the given refresh token (if
any) as `revoked=true, revoked_at=now`.

### 6.4 Self-registration + email verification

`POST /user` (public) creates a disabled `User` with a random
`registration_code` + expiry → `MailService` sends a Thymeleaf HTML email (if `MAIL_ENABLED`) → user calls
`POST /user/validate_email` (public) with
the code → `AccountValidator` validates it and enables the account.
`POST /user/resend_code` (public, rate-limited) re-issues a fresh code.

### 6.5 Scheduled token cleanup

`TokenCleanupService` runs on a schedule (via `AsyncConfig`/Spring
scheduling) to purge expired/revoked rows from `access_tokens` and
`refresh_tokens`, bounding table growth.

## 7. Deployment View

```
┌────────────────────────────────────────────────────────────┐
│ Container / VM                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ design-daas-backend (Spring Boot fat JAR, port 8080) │  │
│  │  - mounts persistent volume: ${OAUTH_KEYS_DIR}       │  │
│  │    (rsa-jwk.json — MUST survive restarts)            │  │
│  └───────────────────┬──────────────────────────────────┘  │
└──────────────────────┼─────────────────────────────────────┘
                       │ JDBC (MariaDB driver)          │ SMTP
                       ▼                                ▼
              ┌──────────────────┐                ┌──────────────┐
              │ MariaDB          │                │ SMTP relay   │
              │ (design_backend) │                │ (e.g. Gmail) │
              └──────────────────┘                └──────────────┘
```

- Packaged as a Docker image (`Dockerfile`); run with
  `docker run -p 8080:8080 -v ${PWD}/keys:/app/keys --env-file .env ...`.
- Configuration is 100% environment-variable driven (12-factor), with
  Spring profiles `local` (default) / `dev` / `prod`
  (`application-{profile}.yml`).
- Flyway runs automatically on startup (`V1__init.sql` … `V4__*.sql`);
  Hibernate validates the mapped entities against the resulting schema but
  never generates DDL itself.
- Being stateless (JWT + DB-backed revocation, no server-side HTTP
  sessions), multiple instances can run behind a load balancer sharing one
  MariaDB instance — the only non-shareable per-instance state is the RSA
  signing key, which must be mounted from shared/persistent storage (or
  identical across instances) for tokens to validate across replicas.

## 8. Cross-cutting Concepts

### 8.1 Permission cascade (the core design-daas contract)

`PermissionService.checkPermission(userId, functionName)` evaluates, in
order of specificity, the **first matching rule wins**:

1. Direct `user → endpoint` rule (`endpoint_user_access`)
2. `user's group → endpoint` rule (`endpoint_user_group_access`)
3. `user → endpoint-group` rule (`endpoint_group_user_access`)
4. `user's group → endpoint-group` rule (`endpoint_group_user_group_access`)

If multiple rows match within the *same* step (e.g. conflicting group
memberships), an explicit **deny beats allow** (`allMatch` over the
booleans). If **no rule matches at any step, the default is deny**
(fail-closed). Unknown users or blank function names also default to
deny without querying any rule. This logic is a direct, documented port
of the old PHP backend's `PermissionCalculationService`, so the
DaaS-Anwendung's permission checks keep the same semantics against the
new Java backend.

### 8.2 Security

- **Stateless JWT resource-server auth** (`SecurityConfig`): CSRF disabled (no cookies/sessions),
  `SessionCreationPolicy.STATELESS`, every endpoint
  requires authentication except an explicit allow-list (registration,
  email verification, token issuance, `/version`, and conditionally
  Swagger UI).
- **Scopes**: `SCOPE_admin` / `SCOPE_user` (`Authorities`), enforced via
  `@PreAuthorize`/`hasAuthority` and `EnableMethodSecurity`.
- **Password hashing**: BCrypt (`PasswordConfig`); `PasswordPolicy` enforces
  minimum strength on registration/password changes.
- **Account lockout**: `AccountLockoutService` tracks
  `failed_login_attempts`/`locked_until` per user.
- **Token revocation**: both access tokens (`jti`) and refresh tokens are
  persisted with `revoked`/`revoked_at`, enabling logout and admin-forced
  session invalidation (`UserSessionsInvalidatedEvent`) despite JWTs being
  normally stateless/self-contained.
- **Rate limiting**: `RateLimitingFilter` + per-route `TokenBucket`s guard
  `/oauth2/user/token`, `/user` (register), `/user/validate_email`,
  `/user/resend_code`; trusted-proxy ranges are configurable (`RATE_LIMIT_TRUSTED_PROXIES`) so `ClientIpResolver` can
  trust
  `X-Forwarded-For` only from known proxies.
- **PII handling**: `LogMasking` masks sensitive fields (e.g. emails) in
  logs; JWTs embed `email`/`name` claims and are explicitly documented as
  carrying personal data.
- **CORS**: configurable allowed origins (`CorsConfig`/`CorsProperties`),
  defaulting to `app.frontend.url`.

### 8.3 Persistence

- JPA/Hibernate for ORM; **Flyway-only** schema evolution (`ddl-auto: validate`), migrations under
  `src/main/resources/db/migration/V{n}__*.sql`.
- MariaDB in all real environments; H2 in-memory for the test profile.
- Core tables: `users`, `user_groups`, `users_to_user_groups`,
  `desktop_groups`, `desktops`, `desktops_to_desktop_groups`,
  `user_group_to_desktop_group`, `endpoints`, `endpoint_groups`,
  `endpoint_group_members`, the four `endpoint*_access` permission tables,
  `refresh_tokens`, `access_tokens`.

### 8.4 Error handling

- `GlobalExceptionHandler` (shared) plus domain-specific handlers (`TokenExceptionHandler`, `SessionExceptionHandler`)
  map typed exceptions (e.g. `InvalidCredentialsException`, `AccountLockedException`,
  `UserNotFoundException`, `WeakPasswordException`) to a consistent
  `ErrorResponse` JSON shape and appropriate HTTP status codes.

### 8.5 API documentation

springdoc-openapi generates `/v3/api-docs`; Swagger UI at
`/swagger-ui.html`, gated behind `SCOPE_admin` unless
`app.security.swagger-public=true`.

### 8.6 Architecture governance

ArchUnit tests (`ArchitectureTest`) run as part of `mvn test`/`verify` and
fail the build on layering violations, naming-convention violations, or
forbidden dependencies (see §5.1). JaCoCo enforces ≥70% line coverage on
`mvn verify`.

## 9. Architecture Decisions (ADRs, condensed)

| Decision                                                                              | Rationale                                                                                                                                         | Alternatives considered                                            |
|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| Custom RS256 JWT issuance instead of full Spring Authorization Server                 | Only two grant types needed (`password`, `refresh_token`); the Frontend posts raw credentials directly, as the old PHP backend's contract expects | Full `spring-security-oauth2-authorization-server`                 |
| DB-backed revocation lists alongside stateless JWTs                                   | Need server-side logout/forced-invalidation despite JWTs' normal statelessness                                                                    | Short-lived access tokens only (no revocation); session-based auth |
| 4-step fail-closed permission cascade ported verbatim from the old PHP backend        | Must preserve exact semantics so the DaaS-Anwendung's existing permission checks keep working unchanged                                           | Rewriting the permission model (rejected — behavior-change risk)   |
| Flyway-only schema, Hibernate `ddl-auto=validate`                                     | Explicit, reviewable, reproducible schema changes; avoids Hibernate auto-DDL surprises across environments                                        | Hibernate `update`/`create-drop`                                   |
| Per-module `controller → service → repository → model` layering, enforced by ArchUnit | Keeps a fast-growing multi-domain codebase consistent and reviewable without manual policing                                                      | Hexagonal/ports-and-adapters per module (heavier for this scope)   |
| `shared` package forbidden from depending on feature modules                          | Keeps cross-cutting code a true leaf dependency, preventing circular coupling                                                                     | Allowing shared utilities to import feature-specific types         |

## 10. Quality Requirements (quality tree, abbreviated)

- **Security**
    - Fail-closed permission default (deny on any ambiguity/unknown input).
    - Stateless, signed, revocable JWTs; BCrypt password hashing; account
      lockout; rate limiting on sensitive public endpoints.
- **Maintainability**
    - Enforced layering/naming/dependency-direction via ArchUnit, checked on
      every build.
    - ≥70% line coverage gate (JaCoCo) on `mvn verify`.
- **Compatibility**
    - API shapes and `function_name` semantics match the old PHP backend's
      contract for the permission cascade, as documented in `PermissionService`.
- **Operability**
    - Stateless app tier (horizontally scalable); Actuator health endpoint;
      externalized config via environment variables; Dockerized.

## 11. Risks and Technical Debt

- **Single point of failure for token validity**: losing
  `${OAUTH_KEYS_DIR}/rsa-jwk.json` invalidates every previously issued JWT
  across all instances — this file must be on persistent, backed-up
  storage, and identical across horizontally scaled replicas.
- **Default admin credentials**: `ADMIN_EMAIL`/`ADMIN_PASSWORD` default to
  a well-known value (`admin@design.local` / `Admin123!`) — the README
  explicitly flags this must be changed before any real deployment; an
  unattended default-credential deployment is a real risk.
- **Permission cascade complexity**: the 4-step cascade with per-step
  "explicit deny wins" conflict resolution is subtle; incorrect new rule
  data (duplicate/conflicting rows) degrades silently to a logged warning
  rather than a hard error.
- **Coupling to an undocumented contract**: the exact `function_name`
  semantics and permission cascade are a port of the old PHP backend's
  `PermissionCalculationService`, which is **not** part of this repository
  — its source is not available here to cross-check, so behavioral drift
  can only be caught via the Java-side tests and the code comments
  documenting the port.
- **Mail delivery is best-effort**: `MAIL_ENABLED=false` by default; when
  disabled, registration emails are not sent, which may silently strand
  users mid-registration in non-dev environments if misconfigured.

## 12. Glossary

| Term                       | Meaning                                                                                                     |
|----------------------------|-------------------------------------------------------------------------------------------------------------|
| DaaS                       | Desktop-as-a-Service — the broader platform this backend supports                                           |
| Function / `function_name` | A named operation the DaaS-Anwendung asks about; the permission cascade allows/denies it per user           |
| Endpoint / Endpoint-group  | This system's representation of a "function" / a named set of functions, used by the permission cascade     |
| Scope                      | OAuth2-style authority embedded in the JWT (`SCOPE_user`, `SCOPE_admin`) controlling endpoint access        |
| `jti`                      | JWT ID claim; unique per access token, used as the revocation key                                           |
| Grant type                 | OAuth2 grant used at `/oauth2/user/token`: `password` or `refresh_token`                                    |
| User-group / Desktop-group | Grouping entities that drive both the permission cascade (user-groups) and desktop-to-user-group assignment |
