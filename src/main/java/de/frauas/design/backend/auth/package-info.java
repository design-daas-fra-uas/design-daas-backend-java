/**
 * Authentication: issuing, refreshing, checking and revoking tokens.
 *
 * <p>{@code POST /oauth2/user/token} handles the password and refresh-token grants and
 * returns an RS256-signed JWT access token plus an opaque, single-use refresh token.
 * Every access token's {@code jti} is stored in {@code access_tokens}, and
 * {@link de.frauas.design.backend.auth.service.TokenRevocationValidator} rejects revoked
 * tokens and disabled users on every request. Other modules end a user's sessions by
 * publishing {@link de.frauas.design.backend.shared.event.UserSessionsInvalidatedEvent}.</p>
 */
package de.frauas.design.backend.auth;
