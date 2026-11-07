[README](../README.md) · [User manual](user-manual.md) · [API](api.md) · [Database](database.md) · [Architecture (arc42)](arc42.md) · [Development](development.md) · [Operations](operations.md)

# API reference

Interactive documentation: **`/swagger-ui.html`**. Raw spec: **`/v3/api-docs`**. Both are public in
`local`/`dev`, admin-only otherwise, and disabled in `prod`.

Legend: 🌐 public · 🔑 any authenticated token · 👤 `user` or `admin` scope · 🛡️ `admin` scope only

## Authentication (`auth` module)

| Method | Path | Access | Request | Success | Notes |
|---|---|---|---|---|---|
| POST | `/oauth2/user/token` | 🌐 | form: `grant_type=password`, `username`, `password`, `scope?` | `200` token JSON | Rate limited (20/min/IP) |
| POST | `/oauth2/user/token` | 🌐 | form: `grant_type=refresh_token`, `refresh_token` | `200` token JSON | Rotates the refresh token |
| POST | `/oauth2/user/logout` | 🔑 | query/form: `refresh_token?` | `204` | Idempotent |
| GET | `/oauth2/user/session` | 🔑 | – | `200` `{user_id, name, role}` | `401` with `token_revoked` / `user_disabled` / `invalid_token` |

Token error codes: `invalid_request` (400), `invalid_scope` (400), `unsupported_grant_type` (400),
`invalid_grant` (401: bad credentials, disabled account, invalid, used or expired refresh token),
`account_locked` (429).

## Users (`user` module)

| Method | Path | Access | Request body | Success |
|---|---|---|---|---|
| POST | `/user` | 🌐 | `{name, email, password, groups?: [int]}` | `201` |
| POST | `/user/validate_email` | 🌐 | `{email, registration_code}` | `200` / `400` |
| POST | `/user/resend_code` | 🌐 | `{email}` | `202` (always) |
| GET | `/users` | 🛡️ | – | `200` `[UserDto]` |
| PATCH | `/user/{userId}` | 🛡️ | `{name?, email?, password?, groups?}` | `200` `UserDto` |
| PATCH | `/user/{userId}/enable` | 🛡️ | – | `204` |
| PATCH | `/user/{userId}/disable` | 🛡️ | – | `204` (revokes all of the user's tokens) |
| POST | `/user/request-application/{application}/{isoCode}` | 👤 | – | `200` (e-mails `MAIL_FROM`) |

`UserDto`: `{id, name, email, role, groups: [userGroupId], enabled}`

## Admins

| Method | Path | Access | Request body | Success |
|---|---|---|---|---|
| GET | `/admins` | 🛡️ | – | `200` `[{id, name, email, role}]` |
| POST | `/admin` | 🛡️ | `{name, email, password}` | `201` (enabled immediately) |

## User groups

| Method | Path | Access | Request body | Success |
|---|---|---|---|---|
| GET | `/user_groups` | 🛡️ | – | `200` `[{id, name, description}]` |
| POST | `/user_group` | 🛡️ | `{name?, description?}` (name falls back to description; at least one is required) | `201` |
| PATCH | `/user_group/{id}` | 🛡️ | `{name?, description?, userIds?: [int]}` (`userIds` replaces the whole member list) | `200` |

## Desktops (`desktop` module)

| Method | Path | Access | Request body | Success |
|---|---|---|---|---|
| GET | `/desktops` | 🛡️ | – | `200` `[{id, description, groups: [desktopGroupId]}]` |
| GET | `/desktop/{id}` | 🛡️ | – | `200` / `404` |
| POST | `/desktop` | 🛡️ | `{description, groups?: [{description}]}` (also creates the listed desktop groups) | `201` / `409` if a group description already exists |
| GET | `/desktop_groups` | 🛡️ | – | `200` `[{id, description, desktopIds}]` |
| POST | `/desktop_group` | 🛡️ | `{description}` (must be unique) | `201` / `409` |
| POST | `/desktop_group/{id}/user_group/{userGroupId}` | 🛡️ | – | `201` / `404` / `409` if already linked |

## Permissions (`permission` module)

| Method | Path | Access | Request | Response |
|---|---|---|---|---|
| GET | `/permissions/{functionName}/{userId}` | 🛡️ | – | `{"result":"allow"\|"deny","user":{"id":1,"name":"..."}}` |
| POST | `/permissions_info` | 🔑 | `{"function_name":"...", "token?":"<raw JWT>"}` | same shape; if `token` is given, that token's user is checked, otherwise the caller |

## Infrastructure

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/swagger-ui.html`, `/v3/api-docs/**` | 🌐 or 🛡️ | Depends on profile |
| GET | `/actuator/health` | 🌐 | `{"status":"UP"}` only, no component details. Use it for health probes |

> There are **no** endpoints to delete anything, to manage admins beyond creating them, or to
> manage permission rules (`endpoints`, `endpoint_groups`, `*_access` tables). Those rows must be
> maintained with SQL for now.
