[README](../README.md) · [User manual](user-manual.md) · [API](api.md) · [Database](database.md) · [Architecture (arc42)](arc42.md) · [Development](development.md) · [Operations](operations.md)

# Database

- **Engine:** MariaDB with the InnoDB storage engine.
- **Migrations:** Flyway, `src/main/resources/db/migration`. Hibernate only *validates* the
  schema (`ddl-auto: validate`), so **every schema change needs a new `V<n>__*.sql` file.**
- **Time:** all timestamps are stored as UTC `Instant`s (`TIMESTAMP(6)`), and the app clock is
  `Clock.systemUTC()`.

## Migration history

| Version | Change |
|---|---|
| V1 | Initial schema (users, groups, desktops, permissions, tokens) |
| V2 | `registration_code_timeout` and `registration_used_moment` changed to `TIMESTAMP(6)` (UTC) |
| V3 | Dropped `desktops.name`; desktops are identified by `description` |
| V4 | Dropped `desktop_groups.name`; `description` is now `NOT NULL UNIQUE`. Fails if duplicate or empty descriptions exist, so clean those up first |

## Entity-relationship diagram

```mermaid
erDiagram
    users {
        INT id PK
        VARCHAR role "discriminator: admin | user"
        VARCHAR name
        VARCHAR email UK
        VARCHAR password "BCrypt(12)"
        BOOLEAN enabled
        VARCHAR registration_code
        TIMESTAMP registration_code_timeout
        TIMESTAMP registration_used_moment
        INT failed_login_attempts
        TIMESTAMP locked_until
    }
    user_groups {
        INT id PK
        VARCHAR name UK
        VARCHAR description
    }
    desktops {
        INT id PK
        VARCHAR description
    }
    desktop_groups {
        INT id PK
        VARCHAR description UK
    }
    endpoints {
        INT id PK
        VARCHAR function_name UK
        VARCHAR description
    }
    endpoint_groups {
        INT id PK
        VARCHAR name UK
        VARCHAR description
    }
    access_tokens {
        BIGINT id PK
        VARCHAR jti UK
        INT user_id FK
        VARCHAR scope
        TIMESTAMP issued_at
        TIMESTAMP expires_at
        BOOLEAN revoked
        TIMESTAMP revoked_at
    }
    refresh_tokens {
        BIGINT id PK
        VARCHAR token_value UK
        INT user_id FK
        VARCHAR scope
        TIMESTAMP created_at
        TIMESTAMP expires_at
        BOOLEAN revoked
        TIMESTAMP revoked_at
    }

    users ||--o{ users_to_user_groups : ""
    user_groups ||--o{ users_to_user_groups : ""
    desktops ||--o{ desktops_to_desktop_groups : ""
    desktop_groups ||--o{ desktops_to_desktop_groups : ""
    user_groups ||--o{ user_group_to_desktop_group : ""
    desktop_groups ||--o{ user_group_to_desktop_group : ""
    endpoint_groups ||--o{ endpoint_group_members : ""
    endpoints ||--o{ endpoint_group_members : ""

    users ||--o{ endpoint_user_access : "step 1"
    endpoints ||--o{ endpoint_user_access : ""
    user_groups ||--o{ endpoint_user_group_access : "step 2"
    endpoints ||--o{ endpoint_user_group_access : ""
    users ||--o{ endpoint_group_user_access : "step 3"
    endpoint_groups ||--o{ endpoint_group_user_access : ""
    user_groups ||--o{ endpoint_group_user_group_access : "step 4"
    endpoint_groups ||--o{ endpoint_group_user_group_access : ""

    users ||--o{ access_tokens : ""
    users ||--o{ refresh_tokens : ""
```

## Table overview

| Group | Table | Purpose |
|---|---|---|
| Identity | `users` | All accounts. **Single-table inheritance**: `role` = `admin` → `Admin`, `user` → `User` |
| | `user_groups` | Named groups of regular users |
| | `users_to_user_groups` | m:n join (users ↔ user groups); admins have no groups |
| Desktops | `desktops` | A virtual desktop |
| | `desktop_groups` | Named collection of desktops (unique description) |
| | `desktops_to_desktop_groups` | m:n join |
| | `user_group_to_desktop_group` | m:n: which user group may use which desktop group |
| Permissions | `endpoints` | A callable DESIGN DaaS function (`function_name`) |
| | `endpoint_groups` + `endpoint_group_members` | Bundles of endpoints |
| | `endpoint_user_access` | Rule: user → endpoint (allow/deny) |
| | `endpoint_user_group_access` | Rule: user group → endpoint |
| | `endpoint_group_user_access` | Rule: user → endpoint group |
| | `endpoint_group_user_group_access` | Rule: user group → endpoint group |
| Auth | `access_tokens` | One row per issued JWT (`jti`), for revocation |
| | `refresh_tokens` | Opaque 256-bit refresh tokens |

All foreign keys use `ON DELETE CASCADE`: deleting a user removes their group memberships, rules
and tokens.

## Permission resolution

```mermaid
flowchart TD
    A[checkPermission user, function] --> B{user exists?}
    B -- no --> DENY[deny]
    B -- yes --> S1{Step 1: user → endpoint rule?}
    S1 -- match --> R[result of that step]
    S1 -- none --> S2{Step 2: user's groups → endpoint rule?}
    S2 -- match --> R
    S2 -- none --> S3{Step 3: user → endpoint-group rule?}
    S3 -- match --> R
    S3 -- none --> S4{Step 4: user's groups → endpoint-group rule?}
    S4 -- match --> R
    S4 -- none --> DENY
```

- The **first step that matches** decides, from most specific to least specific.
- If several rows match in one step, **any deny wins** (fail-closed).
- If nothing matches, the result is **deny**.

Example of seeding rules with SQL (there is no API for this yet):

```sql
INSERT INTO endpoints (function_name, description) VALUES ('start_desktop', 'Start a VM');
INSERT INTO endpoint_user_group_access (user_group_id, endpoint_id, allow_access)
VALUES (1, LAST_INSERT_ID(), TRUE);
```
