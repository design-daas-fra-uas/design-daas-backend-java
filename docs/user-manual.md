# User manual

> **Audience:** users, administrators and frontend developers
> **Last reviewed:** 2026-11-13 · [Documentation index](../README.md#documentation)

This manual walks through the typical tasks with `curl`. The DESIGN DaaS frontend performs the
same calls. Every endpoint is described in full in the [API reference](api.md).

The examples assume the backend runs at `http://localhost:8080`. Replace `<access_token>` and
the ids with real values.

## In short

- **Users** register, confirm their e-mail address with a code, and then log in.
- Logging in returns an **access token**. Send it with every request. It is valid for one hour and
  can be renewed with the **refresh token**.
- **Admins** manage accounts, groups, desktops and permission rules.

## Contents

1. [Roles](#1-roles)
2. [Register and verify an account](#2-register-and-verify-an-account)
3. [Log in](#3-log-in)
4. [Refresh the session and log out](#4-refresh-the-session-and-log-out)
5. [Check the current session](#5-check-the-current-session)
6. [Administration: users and groups](#6-administration-users-and-groups)
7. [Administration: desktops](#7-administration-desktops)
8. [Administration: permissions](#8-administration-permissions)
9. [Troubleshooting](#9-troubleshooting)

## 1. Roles

| Role | Scope in the token | Typical tasks |
|---|---|---|
| **User** | `user` | Register, log in, view the own session, request access to an application |
| **Admin** | `admin` | Manage users, admins, user groups, desktops, desktop groups and permission rules |

Each account has exactly one role, and the token's scope always equals that role. Requesting a
different scope at login fails with `400 invalid_scope`.

## 2. Register and verify an account

Regular users register themselves. The account stays disabled until the e-mail address is
confirmed.

```bash
curl -X POST http://localhost:8080/user \
  -H 'Content-Type: application/json' \
  -d '{"name": "Jane Doe", "email": "jane@example.org", "password": "Secret123"}'
# 201 Created
```

The password must be 8–72 characters long and contain at least one uppercase letter, one
lowercase letter and one digit.

You then receive an e-mail with an 8-character code (digits and `A`–`F`). It is valid for 24 hours.

```bash
curl -X POST http://localhost:8080/user/validate_email \
  -H 'Content-Type: application/json' \
  -d '{"email": "jane@example.org", "registration_code": "3FA9C01B"}'
# 200 OK, or 400 if the code is wrong or expired
```

If the code was lost or has expired, request a new one. Only the newest code is valid.

```bash
curl -X POST http://localhost:8080/user/resend_code \
  -H 'Content-Type: application/json' \
  -d '{"email": "jane@example.org"}'
# 202 Accepted (always, whether or not the address is registered)
```

> **Development tip:** with `MAIL_ENABLED=false`, no e-mail is sent. The code is written to the
> application log at `DEBUG` level instead.

## 3. Log in

The token endpoint accepts **form data only**. A JSON body is rejected with
`415 Unsupported Media Type`.

```bash
curl -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=password \
  -d username=jane@example.org \
  -d password=Secret123
```

```json
{
  "access_token": "eyJraWQiOi…",
  "token_type": "Bearer",
  "refresh_token": "9f2c…",
  "scope": "user",
  "expires_in": 3600
}
```

Send the access token with every further request:

```bash
-H "Authorization: Bearer <access_token>"
```

After **five failed logins in a row**, the account is locked for **15 minutes**
(`429 account_locked`). The counter resets only after a successful login, so a single failure
after the lock expires locks the account again.

## 4. Refresh the session and log out

The access token is valid for one hour by default. Exchange the refresh token for a new pair
before it expires:

```bash
curl -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=refresh_token \
  -d refresh_token=<refresh_token>
```

Each refresh token can be used **once**. Always store the new refresh token from the response.

To log out, revoke the current access token and, ideally, the refresh token:

```bash
curl -X POST "http://localhost:8080/oauth2/user/logout?refresh_token=<refresh_token>" \
  -H "Authorization: Bearer <access_token>"
# 204 No Content
```

All of a user's tokens are also revoked when an admin disables the account, changes its password
or deletes it.

## 5. Check the current session

```bash
curl http://localhost:8080/oauth2/user/session \
  -H "Authorization: Bearer <access_token>"
# {"user_id": 7, "name": "Jane Doe", "role": "user"}
```

## 6. Administration: users and groups

All requests in sections 6–8 need an admin token.

| Task | Request |
|---|---|
| List users | `GET /users` |
| Change name, e-mail, password or groups | `PATCH /user/{id}` with only the fields to change |
| Disable or re-enable an account | `PATCH /user/{id}/disable`, `PATCH /user/{id}/enable` |
| Delete a user (the user is notified by e-mail) | `DELETE /user/{id}` |
| Create another admin | `POST /admin` with `{name, email, password}` |
| Delete an admin | `DELETE /admin/{id}`. You cannot delete your own account or the last admin |

Example: create a user group for a course and set its members.

```bash
curl -X POST http://localhost:8080/user_group \
  -H "Authorization: Bearer <access_token>" -H 'Content-Type: application/json' \
  -d '{"name": "WS26 Students", "description": "Winter semester 2026"}'

curl -X PATCH http://localhost:8080/user_group/3 \
  -H "Authorization: Bearer <access_token>" -H 'Content-Type: application/json' \
  -d '{"userIds": [7, 8, 9]}'
```

`userIds` replaces the complete member list. To add one member, send the existing ids plus the
new one.

## 7. Administration: desktops

```bash
# Create a desktop together with a new desktop group
curl -X POST http://localhost:8080/desktop \
  -H "Authorization: Bearer <access_token>" -H 'Content-Type: application/json' \
  -d '{"description": "Ubuntu 24.04 CAD", "groups": [{"description": "CAD Lab"}]}'

# Give user group 3 access to desktop group 5
curl -X POST http://localhost:8080/desktop_group/5/user_group/3 \
  -H "Authorization: Bearer <access_token>"
```

Desktop group descriptions must be unique. Use `GET /desktops` and `GET /desktop_groups` to look
up ids.

## 8. Administration: permissions

The DaaS application asks the backend whether a user may call a function. For regular users the
answer comes from rules. **Admins are always allowed.** If no rule matches, the answer is deny.

The functions themselves (the endpoints) are not created by hand: the DaaS application sends its
complete list to `PUT /endpoints/sync` every time it starts. A function it no longer has is deleted
together with its rules and group memberships.

Example: allow user group 3 to start environments (the DaaS function `environment_start`).

```bash
# 1. Look up the id of the function
curl http://localhost:8080/endpoints -H "Authorization: Bearer <access_token>"

# 2. Add an allow rule for the group (endpoint id 12)
curl -X POST http://localhost:8080/permission_rule \
  -H "Authorization: Bearer <access_token>" -H 'Content-Type: application/json' \
  -d '{"kind": "USER_GROUP_ENDPOINT", "subjectId": 3, "targetId": 12, "allow": true}'

# 3. Check the result for user 7
curl http://localhost:8080/permissions/environment_start/7 -H "Authorization: Bearer <access_token>"
# {"result": "allow", "user": {"id": 7, "name": "Jane Doe"}}
```

To grant many functions at once, bundle them in an endpoint group (`POST /endpoint_group`) and
use the kinds `USER_ENDPOINT_GROUP` or `USER_GROUP_ENDPOINT_GROUP`. A rule for a single user
overrides a group rule, and a rule for an endpoint overrides an endpoint-group rule. The full
order is described in [Permission resolution](database.md#permission-resolution).

Good to know:

- The DaaS application checks permissions only when its setting `DAAS_AUTH_CHECK_ENDPOINTS` is
  `true`. Before switching it on, create the endpoints and rules your users need; otherwise every
  function is denied to them.
- The DaaS application remembers each answer for 30 seconds. After changing a rule, wait up to
  30 seconds before testing it there.
- Rules cannot name an admin account; admins are always allowed.

## 9. Troubleshooting

| Response | Meaning and remedy |
|---|---|
| `401` on any request | The token is missing, expired or revoked, or the account was disabled. Log in again |
| `403 Access denied` | The request needs an admin token |
| `415` on login | Send form data (`-d key=value`), not JSON |
| `429 account_locked` | Wait 15 minutes, then log in with the correct password. Admins cannot lift the lock early |
| `429 too_many_requests` | Too many requests from your IP address; wait one minute |
| `500` with a support reference | Give the reference to the operators. It is the request id and identifies every log line of the request |
