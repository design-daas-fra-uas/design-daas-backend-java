package de.frauas.design.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of a valid session, returned by {@code SessionService#getSession} for
 * {@code GET /oauth2/user/session}.
 *
 * <p>If the session is invalid for any reason (revoked token, disabled/missing user, ...),
 * the service throws a {@link de.frauas.design.backend.auth.exception.SessionException}
 * subtype instead of returning this type — see that class for why exceptions are used here.</p>
 *
 * @param userId the user's numeric id (the JWT's {@code sub} claim)
 * @param name   the user's display name
 */
public record SessionInfoDto(
        @JsonProperty("user_id") Integer userId,
        @JsonProperty("name") String name) {}
