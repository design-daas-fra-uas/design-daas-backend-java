package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.shared.event.UserSessionsInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Revokes issued tokens: on explicit logout, and for all of a user's tokens when their
 * account is disabled or their password changes (see {@link UserSessionsInvalidatedEvent}).
 *
 * <p>Revoked access tokens are rejected on the next request by
 * {@link TokenRevocationValidator}; revoked refresh tokens can no longer be redeemed.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final AccessTokenRepository accessTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /**
     * Revokes the caller's current access token and, if supplied, their refresh token.
     * Idempotent: unknown or already-revoked tokens are ignored, and a refresh token that
     * belongs to a different user is never revoked.
     *
     * @param userId       the authenticated caller
     * @param accessJti    the {@code jti} of the access token used for the request
     * @param refreshToken the refresh token to revoke too, may be {@code null}/blank
     */
    @Transactional
    public void logout(Integer userId, String accessJti, String refreshToken) {
        if (accessJti != null) {
            accessTokenRepository.findByJti(accessJti).ifPresent(token -> {
                token.revoke();
                accessTokenRepository.save(token);
            });
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository
                    .findActiveByTokenValue(refreshToken)
                    .filter(token -> token.getUserId().equals(userId))
                    .ifPresent(token -> {
                        token.revoke();
                        refreshTokenRepository.save(token);
                    });
        }
        log.info("logout — tokens revoked userId={}", userId);
    }

    /**
     * Revokes every still-active access and refresh token of a user.
     *
     * @param userId the user whose sessions are terminated
     */
    @Transactional
    public void revokeAllForUser(Integer userId) {
        Instant now = Instant.now(clock);
        int access = accessTokenRepository.revokeAllByUserId(userId, now);
        int refresh = refreshTokenRepository.revokeAllByUserId(userId, now);
        log.info("revokeAllForUser — userId={} accessTokens={} refreshTokens={}", userId, access, refresh);
    }

    @EventListener
    @Transactional
    public void onUserSessionsInvalidated(UserSessionsInvalidatedEvent event) {
        revokeAllForUser(event.userId());
    }
}
