[README](../README.md) · [User manual](user-manual.md) · [API](api.md) · [Database](database.md) · [Architecture (arc42)](arc42.md) · [Development](development.md) · [Operations](operations.md)

# User manual

This section is for people who use the API through the DESIGN DaaS frontend or with tools such as
curl or Postman.

## Roles

| Role | Scope in token | Can do |
|---|---|---|
| **user** | `user` | Register, verify e-mail, log in, see own session, request application access, check own permissions |
| **admin** | `admin` | Everything: manage users, admins, user groups, desktops, desktop groups, check anyone's permissions, use Swagger |

Every account has exactly one role. The role is also the only scope a token can carry. Asking for
a different scope at login returns `400 invalid_scope`.

## 1. Register and verify (regular user)

```bash
# Register; the account stays disabled until the e-mail is verified
curl -X POST http://localhost:8080/user -H 'Content-Type: application/json' \
  -d '{"name":"Jane Doe","email":"jane@example.org","password":"Secret123"}'
# -> 201 Created

# An 8-character code (0-9, A-F) arrives by e-mail and is valid for 24 h.
# With MAIL_ENABLED=false, it appears in the log at DEBUG level.
curl -X POST http://localhost:8080/user/validate_email -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.org","registration_code":"3FA9C01B"}'
# -> 200 OK (or 400 if the code is wrong or expired)

# Lost the code? Request a new one (always returns 202, so it cannot reveal accounts)
curl -X POST http://localhost:8080/user/resend_code -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.org"}'
```

**Password policy:** 8–72 characters, with at least one uppercase letter, one lowercase letter and
one digit.

## 2. Log in

The token endpoint only accepts **form parameters** (`application/x-www-form-urlencoded`), as the
OAuth2 standard requires. A JSON body is rejected with `415 Unsupported Media Type`.

```bash
curl -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=password -d username=jane@example.org -d password=Secret123
```

```json
{
  "access_token": "eyJraWQiOi...",
  "token_type": "Bearer",
  "refresh_token": "9f2c...64 hex chars...",
  "scope": "user",
  "expires_in": 3600
}
```

Send the access token on every other call: `Authorization: Bearer <access_token>`.

After **5 wrong passwords in a row**, the account is locked for **15 minutes** (`429 account_locked`).

## 3. Refresh and log out

```bash
# Get a new token pair; the old refresh token stops working (rotation)
curl -X POST http://localhost:8080/oauth2/user/token \
  -d grant_type=refresh_token -d refresh_token=<refresh_token>

# Log out; revokes this access token and, optionally, the refresh token
curl -X POST "http://localhost:8080/oauth2/user/logout?refresh_token=<refresh_token>" \
  -H "Authorization: Bearer <access_token>"
# -> 204 No Content
```

All of a user's tokens are also revoked automatically when an admin **disables** the user or
**changes their password**.

## 4. "Who am I?"

```bash
curl http://localhost:8080/oauth2/user/session -H "Authorization: Bearer <access_token>"
# {"user_id": 7, "name": "Jane Doe", "role": "user"}
```

## 5. Typical admin workflow

```text
1. POST  /user_group                        {"name":"WS24 Students","description":"..."}
2. PATCH /user_group/{id}                   {"userIds":[7,8,9]}          (sets the full member list)
3. POST  /desktop                           {"description":"Ubuntu CAD","groups":[{"description":"CAD Lab"}]}
4. POST  /desktop_group/{dgId}/user_group/{ugId}                         (grants the user group the desktop group)
5. GET   /permissions/{functionName}/{userId}                            (checks a permission)
```

## Error format

| Area | Body |
|---|---|
| Token / session endpoints | `{"error": "invalid_grant", "error_description": "Invalid credentials"}` (OAuth2 style) |
| Rate limit | `429` + `Retry-After: 60` + `{"error":"too_many_requests", ...}` |
| Everything else | `{"message": "..."}`. Unexpected errors return `500` with a *support reference* UUID that you can find in the server log |
