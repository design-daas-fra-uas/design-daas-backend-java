package de.frauas.design.backend.shared.event;

/**
 * Published when every session of a user must stop working immediately — for example
 * after the account was disabled or its password was changed. The auth module reacts by
 * revoking all of the user's access and refresh tokens.
 *
 * <p>Lives in {@code shared} so the user module can trigger revocation without depending
 * on the auth module.</p>
 *
 * @param userId the user whose tokens must be revoked
 */
public record UserSessionsInvalidatedEvent(Integer userId) {}
