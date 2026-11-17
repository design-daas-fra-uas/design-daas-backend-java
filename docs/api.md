# API reference

> **Audience:** developers of the frontend and of the DaaS application (design-daas)
> **Last reviewed:** 2026-11-13 · [Documentation index](../README.md#documentation)

This document lists every HTTP endpoint of the backend: who may call it, what to send, and what
comes back. For exact field types, use the OpenAPI description that is generated from the code:

- `/swagger-ui.html`: interactive, lets you try the requests in the browser
- `/v3/api-docs`: the same description as JSON

Both are public in the `local` and `dev` profiles, admin-only in other profiles, and switched off
in `prod`.

## Contents

1. [Your first request](#1-your-first-request)
2. [General rules](#2-general-rules)
3. [Authentication](#3-authentication)
4. [Users](#4-users)
5. [Admins](#5-admins)
6. [User groups](#6-user-groups)
7. [Desktops and desktop groups](#7-desktops-and-desktop-groups)
8. [Permission check](#8-permission-check)
9. [Permission management](#9-permission-management)
10. [Health and API description](#10-health-and-api-description)
11. [Field validation reference](#11-field-validation-reference)
12. [E-mails sent by the backend](#12-e-mails-sent-by-the-backend)
13. [Integration notes](#13-integration-notes)
14. [Differences from the PHP backend](#14-differences-from-the-php-backend)

## 1. Your first request

Almost every endpoint needs an **access token**. You get one by logging in, and then send it with
each request.

**Step 1: log in.** The login endpoint expects form data, not JSON.

```bash
curl -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=password -d username=admin@design.local -d 'password=Admin123!'
```

**Step 2: copy `access_token` from the response.**

```json
{ "access_token": "eyJraWQiOi…", "token_type": "Bearer", "refresh_token": "9f2c…", "scope": "admin", "expires_in": 3600 }
```

**Step 3: send it in the `Authorization` header.**

```bash
curl http://localhost:8080/users -H "Authorization: Bearer eyJraWQiOi…"
```

The access token is valid for one hour. Section 3 explains how to renew it.

## 2. General rules

### Who may call an endpoint

Each section states the access level of its endpoints:

| Access level | Meaning |
|---|---|
| **Public** | No token needed |
| **Any token** | Any logged-in account, user or admin |
| **User** | An account with the role `user` or `admin` |
| **Admin** | An account with the role `admin` |

A token stops working when it expires, when the user logs out, or when an admin disables,
changes or deletes the account. The backend checks this against the database on every request,
so the effect is immediate.

### Requests

| Topic | Rule |
|---|---|
| Base URL | `http://localhost:8080` when running locally |
| Body format | JSON (`Content-Type: application/json`). Exception: the token endpoint accepts only form data |
| Field names | camelCase. The login and session responses use snake_case (`access_token`, `user_id`) for compatibility with the PHP backend |
| Creating | A successful `POST` returns `201 Created` with an **empty body**. To get the new id, call the list endpoint |
| Changing | `PATCH` changes only the fields you send. A list field (`groups`, `userIds`, `endpointIds`) replaces the whole list |
| Request id | Optional header `X-Request-ID` (1–64 characters: letters, digits, `.`, `_`, `-`). The backend returns it in the response and writes it into its logs. Without it, the backend creates one |

### Errors

| Status | Meaning | Response body |
|---|---|---|
| `400` | The request is invalid: a missing or malformed field, a weak password, an e-mail address that is already used, or an unknown id in a list | `{"message": "field: reason"}` |
| `401` | No token, or the token is invalid, expired or revoked | Empty |
| `403` | The token is valid, but the account's role is not allowed to do this | `{"message": "Access denied"}` |
| `404` | The resource does not exist | `{"message": "Desktop not found: 12"}` |
| `409` | The request conflicts with existing data, for example a duplicate name | `{"message": "…"}` |
| `415` | Wrong `Content-Type`, for example JSON sent to the login endpoint | `{"message": "…"}` |
| `429` | Too many requests from your IP address, or the account is locked | See below |
| `500` | Unexpected server error | `{"message": "An unexpected error occurred. Support reference: <id>"}` |

The support reference in a `500` response is the request id. Give it to the operators: it lets
them find the request in the logs.

The login and session endpoints return errors in the OAuth2 format instead:
`{"error": "invalid_grant", "error_description": "Invalid credentials"}`.

## 3. Authentication

| Method | Path | Access | Send | Returns |
|---|---|---|---|---|
| `POST` | `/oauth2/user/token` | Public | Form: `grant_type=password`, `username`, `password` | `200` with tokens |
| `POST` | `/oauth2/user/token` | Public | Form: `grant_type=refresh_token`, `refresh_token` | `200` with new tokens |
| `POST` | `/oauth2/user/logout` | Any token | Optional form or query parameter `refresh_token` | `204` |
| `GET` | `/oauth2/user/session` | Any token | – | `200` `{"user_id": 7, "name": "Jane Doe", "role": "user"}` |

**Logging in** returns two tokens:

| Field | Meaning |
|---|---|
| `access_token` | Sent with every request. Valid for one hour (`expires_in`, in seconds) |
| `refresh_token` | Used once to get a new pair of tokens without entering the password again. Valid for 30 days |
| `scope` | The account's role: `user` or `admin` |

**Renewing:** send the refresh token with `grant_type=refresh_token`. You receive a new access
token **and a new refresh token**. The old refresh token no longer works, so always keep the new
one.

**Logging out** ends the access token you call it with. If you also send the refresh token, it
ends that one too. Calling it twice does no harm.

**Limits:** at most 20 login requests per minute from one IP address. After five wrong passwords
in a row, the account is locked for 15 minutes.

**What is inside the access token.** The access token is a JSON Web Token (JWT): three
Base64-encoded parts separated by dots. The header names the signing algorithm `RS256` and the
key id (`kid`). The payload contains these claims:

| Claim | Content | Example |
|---|---|---|
| `jti` | Unique token id; the backend uses it to revoke the token | `"0f8e…-4c1a"` |
| `iss` | Issuer: the backend's public URL (`OAUTH_ISSUER`) | `"https://daas.example.org:8443/auth"` |
| `sub` | Account id, as a string | `"7"` |
| `iat`, `exp` | Issued at and expiry time, in seconds since 1970 | `1794560000` |
| `email`, `name` | The account's e-mail address and display name | `"jane@example.org"` |
| `scope` | The account's role | `"user"` |

Clients may read the payload, for example to show the user's name. They must not rely on it for
security decisions: only the backend can tell whether a token was revoked. To check a token, call
`GET /oauth2/user/session`. The backend does not publish its public key (there is no JWKS
endpoint), so other services cannot verify tokens on their own.

Because the token contains the e-mail address and name, treat it as personal data: do not log it
and do not store it longer than needed.

The refresh token is a random value without inner structure (64 hexadecimal characters).

| Error code | Status | Cause |
|---|---|---|
| `invalid_request` | 400 | A required form field is missing |
| `unsupported_grant_type` | 400 | `grant_type` is not `password` or `refresh_token` |
| `invalid_scope` | 400 | `scope` was sent and differs from the account's role. Leave `scope` out |
| `invalid_grant` | 401 | Wrong e-mail or password, disabled account, or an unknown, used or expired refresh token |
| `account_locked` | 429 | The account is locked after five failed logins |
| `missing_token`, `invalid_token`, `token_revoked`, `user_disabled` | 401 | Returned by the session endpoint only |

## 4. Users

| Method | Path | Access | Send | Returns | Possible errors |
|---|---|---|---|---|---|
| `POST` | `/user` | Public | `{name, email, password, groups?}` | `201` | `400` |
| `POST` | `/user/validate_email` | Public | `{email, registration_code}` | `200` | `400` wrong or expired code |
| `POST` | `/user/resend_code` | Public | `{email}` | `202` | – |
| `GET` | `/users` | Admin | – | `200` list of users | – |
| `GET` | `/user/{userId}` | Admin | – | `200` user | `404` |
| `PATCH` | `/user/{userId}` | Admin | `{name?, email?, password?, groups?}` | `200` user | `400`, `404` |
| `PATCH` | `/user/{userId}/enable` | Admin | – | `204` | `404` |
| `PATCH` | `/user/{userId}/disable` | Admin | – | `204` | `404` |
| `DELETE` | `/user/{userId}` | Admin | – | `204` | `404` |
| `POST` | `/user/request-application/{application}/{isoCode}` | User | – | `200` | `400` |

A user looks like this:

```json
{ "id": 7, "name": "Jane Doe", "email": "jane@example.org", "role": "user", "groups": [3], "enabled": true }
```

**Registration in three steps:**

1. `POST /user` creates the account. It stays disabled.
2. The user receives an e-mail with an 8-character code, valid for 24 hours.
3. `POST /user/validate_email` with that code enables the account.

`POST /user/resend_code` sends a new code. It always answers `202`, even for unknown addresses, so
nobody can use it to find out which addresses are registered.

**Password rules:** 8 to 72 characters, with at least one uppercase letter, one lowercase letter
and one digit.

**Effects of admin actions:**

| Action | Effect |
|---|---|
| Disable | The user is logged out everywhere and cannot log in |
| Change the password | The user is logged out everywhere |
| Delete | The account, its tokens, group memberships and permission rules are removed, and the user receives an e-mail |

**Application request:** sends an e-mail to the address in `MAIL_FROM`; replying to it reaches the
user. `{application}` may contain letters, digits, spaces, `_`, `.` and `-` (at most 100
characters). `{isoCode}` is checked but currently not used.

**Limits:** per IP address and minute, 5 registrations, 10 code validations and 3 code resends.

## 5. Admins

All endpoints in this section need an **admin** token.

| Method | Path | Send | Returns | Possible errors |
|---|---|---|---|---|
| `GET` | `/admins` | – | `200` list of admins | – |
| `GET` | `/admin/{adminId}` | – | `200` admin | `404` |
| `POST` | `/admin` | `{name, email, password}` | `201` | `400` |
| `DELETE` | `/admin/{adminId}` | – | `204` | `404`; `409` see below |

An admin looks like this: `{"id": 1, "name": "Default Admin", "email": "admin@design.local", "role": "admin"}`.

- New admins can log in immediately; they need no e-mail confirmation.
- An admin cannot delete their own account, and the last remaining admin cannot be deleted. Both
  return `409`.
- A deleted admin receives an e-mail.

## 6. User groups

All endpoints in this section need an **admin** token. A user group collects regular users, for
example the students of one course.

| Method | Path | Send | Returns | Possible errors |
|---|---|---|---|---|
| `GET` | `/user_groups` | – | `200` `[{id, name, description}]` | – |
| `POST` | `/user_group` | `{name?, description?}` | `201` | `400` if both are empty; `409` name already used |
| `PATCH` | `/user_group/{id}` | `{name?, description?, userIds?}` | `200` `{id, name, description}` | `400`, `404` |
| `DELETE` | `/user_group/{id}` | – | `204` | `404` |

- If `name` is empty, the description is used as the name.
- `userIds` is the **complete** member list. To add one member, send the current members plus the
  new one. Admins cannot be members.
- Deleting a group keeps its members. The group's desktop access and its permission rules are
  removed.

## 7. Desktops and desktop groups

All endpoints in this section need an **admin** token. The backend only keeps the list of
desktops; it does not start or stop them.

| Method | Path | Send | Returns | Possible errors |
|---|---|---|---|---|
| `GET` | `/desktops` | – | `200` `[{id, description, groups}]` | – |
| `GET` | `/desktop/{id}` | – | `200` desktop | `404` |
| `POST` | `/desktop` | `{description, groups?: [{description}]}` | `201` | `409` group description already used |
| `DELETE` | `/desktop/{id}` | – | `204` | `404` |
| `GET` | `/desktop_groups` | – | `200` `[{id, description, desktopIds}]` | – |
| `POST` | `/desktop_group` | `{description}` | `201` | `409` description already used |
| `POST` | `/desktop_group/{id}/user_group/{userGroupId}` | – | `201` | `404`; `409` already linked |
| `DELETE` | `/desktop_group/{id}` | – | `204` | `404` |

How access to desktops works:

1. Desktops are put into **desktop groups**.
2. A desktop group is linked to one or more **user groups**.
3. Every member of a linked user group may use every desktop in the desktop group.

Notes:

- Desktops and desktop groups are identified by their description. Desktop group descriptions
  must be unique.
- The optional `groups` list in `POST /desktop` creates **new** desktop groups that contain the new
  desktop. It cannot add the desktop to existing groups.
- Deleting a desktop keeps its groups. Deleting a desktop group keeps its desktops and the linked
  user groups.

## 8. Permission check

The DaaS application calls these endpoints to decide whether a user may use one of its
functions, for example starting a desktop.

| Method | Path | Access | Send | Returns |
|---|---|---|---|---|
| `GET` | `/permissions/{functionName}/{userId}` | Admin | – | Result |
| `POST` | `/permissions_info` | Any token | `{"function_name": "…", "token": "…"}`, `token` optional | Result |

Result: `{"result": "allow", "user": {"id": 7, "name": "Jane Doe"}}`. `result` is `allow` or
`deny`.

How the answer is found:

1. **Admins** are always allowed.
2. For **regular users**, the backend looks for matching rules in four steps, from the most
   specific to the most general (see [section 9](#9-permission-management)). The first step that
   finds a rule gives the answer.
3. If one step finds both an allow and a deny rule, for example from two groups, **deny wins**.
4. If no step finds a rule, the answer is **deny**. Unknown users and unknown functions are also
   denied.

`POST /permissions_info` checks the account of `token` if you send one, otherwise your own
account. An invalid `token` returns `deny` with user id `0`. Without `function_name` it returns
`400`.

## 9. Permission management

All endpoints in this section need an **admin** token. They maintain the data that the permission
check uses.

| Term | Meaning | Example |
|---|---|---|
| **Endpoint** | One function of the DaaS application, identified by its name | `environment_start` |
| **Endpoint group** | A named set of endpoints, to grant several functions at once | `environment_basics` |
| **Rule** | Allows or denies one **subject** (a user or a user group) one **target** (an endpoint or an endpoint group) | "Group 3 may use `environment_start`" |

There are four kinds of rule. The permission check tries them in this order:

| Order | `kind` | Subject | Target |
|---|---|---|---|
| 1 | `USER_ENDPOINT` | one user | one endpoint |
| 2 | `USER_GROUP_ENDPOINT` | a user group | one endpoint |
| 3 | `USER_ENDPOINT_GROUP` | one user | an endpoint group |
| 4 | `USER_GROUP_ENDPOINT_GROUP` | a user group | an endpoint group |

So a rule for a single user overrides a group rule, and a rule for a single endpoint overrides an
endpoint-group rule.

| Method | Path | Send | Returns | Possible errors |
|---|---|---|---|---|
| `GET` | `/endpoints` | – | `200` `[{id, functionName, description}]` | – |
| `PUT` | `/endpoints/sync` | `{endpoints: [{functionName, description?}]}` | `200` `{added, updated, deleted}` | `400` invalid name |
| `GET` | `/endpoint_groups` | – | `200` `[{id, name, description, endpointIds}]` | – |
| `POST` | `/endpoint_group` | `{name, description?, endpointIds?}` | `201` | `400` unknown endpoint; `409` name already used |
| `PATCH` | `/endpoint_group/{id}` | `{name?, description?, endpointIds?}` | `200` endpoint group | `400`, `404`, `409` |
| `DELETE` | `/endpoint_group/{id}` | – | `204` | `404` |
| `GET` | `/permission_rules` | – | `200` `[{id, kind, subjectId, targetId, targetName, allow}]` | – |
| `POST` | `/permission_rule` | `{kind, subjectId, targetId, allow}` | `201` | `400`; `404` unknown subject or target; `409` rule already exists |
| `DELETE` | `/permission_rule/{kind}/{id}` | – | `204` | `404` |

Notes:

- `functionName` may contain letters, digits, `_`, `.` and `-`.
- There can be only one rule per kind, subject and target. To change a rule, delete it and create
  a new one.
- Rules for a single user must name a regular user. A rule for an admin is rejected with `400`,
  because admins are always allowed and the rule would never be used.
- Rule ids are unique only within one kind. That is why deleting a rule needs both the kind and
  the id.
- Deleting an endpoint or an endpoint group also deletes the rules that point to it. The other
  side is kept: deleting an endpoint group keeps its endpoints.
- `GET /permission_rules` lists the rules in check order.

## 10. Health and API description

| Method | Path | Access | Returns |
|---|---|---|---|
| `GET` | `/actuator/health` | Public | `200 {"status": "UP"}`, or `503` if the database (or, with `MAIL_ENABLED=true`, the mail server) cannot be reached |
| `GET` | `/swagger-ui.html`, `/v3/api-docs` | Depends on the profile | See the introduction |

## 11. Field validation reference

Invalid fields are rejected with `400` and a message of the form `field: reason`. "Text" means a
string; the length is counted in characters.

| Request | Field | Required | Rule |
|---|---|---|---|
| `POST /user` | `name` | yes | Text, at most 255 |
| | `email` | yes | Valid e-mail address, at most 255, not used by another account |
| | `password` | yes | 8–72 characters, at least one uppercase letter, one lowercase letter and one digit |
| | `groups` | no | List of existing user group ids |
| `POST /user/validate_email` | `email` | yes | Valid e-mail address, at most 255 |
| | `registration_code` | yes | Text, at most 64 |
| `POST /user/resend_code` | `email` | yes | Valid e-mail address, at most 255 |
| `PATCH /user/{id}` | `name` | no | Text, 1–255 |
| | `email` | no | Valid e-mail address, at most 255, not used by another account |
| | `password` | no | Same rule as on registration |
| | `groups` | no | List of existing user group ids; replaces the current list |
| `POST /admin` | `name`, `email`, `password` | yes | Same rules as `POST /user` |
| `POST`, `PATCH /user_group` | `name`, `description` | at least one on `POST` | Text, at most 255; `name` must be unique |
| | `userIds` | no (`PATCH` only) | List of existing regular-user ids |
| `POST /desktop` | `description` | yes | Text, at most 255 |
| | `groups[].description` | yes, per entry | Text, at most 255; unique among desktop groups |
| `POST /desktop_group` | `description` | yes | Text, at most 255; unique |
| `PUT /endpoints/sync` | `endpoints[].functionName` | yes, per entry | 1–255 characters from letters, digits, `_`, `.`, `-`; duplicates are ignored |
| | `endpoints[].description` | no | Text; cut to 255 characters |
| `POST /endpoint_group` | `name` | yes | Text, at most 255; unique |
| | `description` | no | Text, at most 255 |
| | `endpointIds` | no | List of existing endpoint ids |
| `PATCH /endpoint_group/{id}` | `name` | no | Text, 1–255; unique |
| `POST /permission_rule` | `kind` | yes | One of the four kinds in section 9 |
| | `subjectId`, `targetId` | yes | Existing ids of the type that `kind` requires |
| | `allow` | yes | `true` or `false` |
| Path variables | `{application}` | – | 1–100 characters from letters, digits, space, `_`, `.`, `-` |
| | `{isoCode}` | – | 1–10 characters from letters, digits, `_`, `-` |

## 12. E-mails sent by the backend

E-mails are sent only when `MAIL_ENABLED=true`. They are sent after the database change has been
saved, in the background; a failed e-mail does not undo the change and is only logged.

| Trigger | Recipient | Subject | Content |
|---|---|---|---|
| `POST /user`, `POST /user/resend_code` | The new user | DESIGN DaaS — Email Verification | The 8-character code and how long it is valid |
| `POST /user/request-application/…` | `MAIL_FROM` (the operators); reply goes to the user | DESIGN DaaS — Application Access Request | User name, id, e-mail address and the requested application |
| `DELETE /user/{id}`, `DELETE /admin/{id}` | The deleted account | DESIGN DaaS — Account Deleted | Notice that the account was deleted and all sessions ended |

The templates are in `src/main/resources/templates/mail/`.

## 13. Integration notes

### Frontend

- In the DESIGN installation, the browser reaches the backend under the frontend's own address,
  at the path `/auth/` (nginx forwards it to port 8080). Because both share one origin, the
  browser needs no CORS. When the frontend runs on another origin, add it to
  `CORS_ALLOWED_ORIGINS`.
- Keep the refresh token and replace it after every renewal. Renew shortly before `expires_in`
  runs out, or when a request returns `401`.
- On logout, send the refresh token too, so that both tokens end.
- Send an `X-Request-ID` with each request. The backend allows browsers to read it from the
  response (`Access-Control-Expose-Headers`).

### DaaS application (design-daas)

- The DaaS application logs in with the **admin account** configured in its installer
  (`ADMIN_EMAIL`, `ADMIN_PASSWORD`). It uses that token to call
  `GET /permissions/{functionName}/{userId}` and `GET /oauth2/user/session`.
- The `functionName` is the name of the DaaS endpoint being called, for example `app_list` or
  `environment_start`. The DaaS application defines about 130 such names (`name=` in its
  `*_routes.py` files, plus `<name>_vm` / `<name>_container` for the create routes). It sends the
  whole list to `PUT /endpoints/sync` at startup (and again every minute until that works), so the
  endpoint list always matches its code. A name missing from the list is deleted together with its
  rules and group memberships.
- The permission check runs only if the DaaS application has `DAAS_AUTH_CHECK_ENDPOINTS=true`. It
  is off by default. When it is switched on, every function that has no rule is denied for regular
  users.
- The DaaS application caches each allow or deny answer for **30 seconds**. A changed rule can
  therefore take up to 30 seconds to take effect there. Revoked tokens are not affected: they are
  checked on every call to the session endpoint.
- The DaaS application sends a `client_id` with its login request. The backend ignores it.

## 14. Differences from the PHP backend

The endpoints that the frontend and the DaaS application use kept their paths, fields and status
codes. These differences are intentional:

| PHP backend | This backend | Reason |
|---|---|---|
| `POST /user/{id}/enable`, `POST /user/{id}/disable` | `PATCH` | Correct HTTP method; the frontend was adapted |
| `POST /desktop-group` | `POST /desktop_group` | The frontend already called this path |
| `GET /oauth2/user/session` | Also returns `role` | The frontend needs it |
| `POST /permissions_info` required an admin token | Any token | Users may check their own permissions |
| `GET /version`, `GET /ping/user`, `GET /ping/admin` | Removed | Replaced by `GET /actuator/health` |
| `GET /users/access/{desktopId}`, `GET /desktop_group/{id}`, `POST /user_group/{ugId}/{dgId}` | Removed | Not used by either client |
| `PUT` and `DELETE /endpoint_group/…` | Replaced | See [section 9](#9-permission-management) |
| `GET /swagger`, `GET /swagger-by-role/{role}` | Replaced | `/swagger-ui.html` and `/v3/api-docs` |
| – | New | `POST /user/resend_code`, `POST /oauth2/user/logout`, single-item `GET` and all `DELETE` endpoints |
