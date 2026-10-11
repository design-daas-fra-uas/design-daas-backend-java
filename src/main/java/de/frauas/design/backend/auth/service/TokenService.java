package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.dto.GrantRequest;
import de.frauas.design.backend.auth.dto.GrantRequest.PasswordGrantRequest;
import de.frauas.design.backend.auth.dto.GrantRequest.RefreshGrantRequest;
import de.frauas.design.backend.auth.dto.TokenResponseDto;
import de.frauas.design.backend.auth.exception.AccountDisabledException;
import de.frauas.design.backend.auth.exception.ExpiredRefreshTokenException;
import de.frauas.design.backend.auth.exception.InvalidCredentialsException;
import de.frauas.design.backend.auth.exception.InvalidRefreshTokenException;
import de.frauas.design.backend.auth.exception.InvalidRequestException;
import de.frauas.design.backend.auth.exception.RefreshTokenUserInvalidException;
import de.frauas.design.backend.auth.exception.TokenGrantException;
import de.frauas.design.backend.auth.model.RefreshTokenEntity;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Handles logging in, refreshing tokens, and logging out.
 *
 * <p>Login (password grant): checks the username/password, makes sure the account
 * isn't locked or disabled, checks the requested scope is allowed, then issues a new
 * access token and refresh token.</p>
 *
 * <p>Refresh (refresh-token grant): checks the given refresh token is still valid,
 * checks the user behind it still exists and is enabled, then issues a new access
 * token and refresh token. The old refresh token is revoked so it can't be reused
 * (token rotation).</p>
 *
 * <p>Logout and bulk revocation live in {@link TokenRevocationService}.</p>
 *
 * <p>The actual lockout rules, scope rules, and token creation live in
 * {@link AccountLockoutService}, {@link ScopeResolver}, and {@link TokenIssuer} —
 * this class just coordinates them.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    /**
     * Dummy BCrypt hash used when a user is not found so that the
     * response time is indistinguishable from a wrong-password attempt.
     */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa3G5Wq/8rEjMvT6T3E2z3aJEOv5bIfy";
    /**
     * Token type advertised to clients, per RFC 6749 §5.1.
     */
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountLockoutService accountLockoutService;
    private final ScopeResolver scopeResolver;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    // -------------------------------------------------------------------------
    // Grant dispatch
    // -------------------------------------------------------------------------

    /**
     * Dispatches to the appropriate grant handler based on the validated {@link GrantRequest},
     * as required by RFC 6749. Extracted from {@code TokenController} so that
     * the controller stays a thin HTTP adapter and this routing is unit-testable
     * alongside the rest of the grant logic.
     *
     * <p>{@link GrantRequest} being {@code sealed} means this {@code switch} is exhaustive:
     * the compiler rejects this method if a new grant variant is ever added without
     * updating the dispatch here.</p>
     *
     * @param request the grant request, already resolved to a specific variant by
     *                {@link GrantRequest#of}
     * @return the newly issued access/refresh token pair
     */
    @Transactional(noRollbackFor = TokenGrantException.class)
    public TokenResponseDto grantToken(GrantRequest request) {
        return switch (request) {
            case PasswordGrantRequest r -> passwordGrant(r.username(), r.password(), r.scope());
            case RefreshGrantRequest r -> refreshGrant(r.refreshToken());
        };
    }

    // -------------------------------------------------------------------------
    // Password grant
    // -------------------------------------------------------------------------

    /**
     * Handles the resource-owner password grant: validates credentials, enforces
     * account lockout and timing-attack mitigation, validates the
     * requested scope, and issues a new access/refresh token pair.
     */
    @Transactional(noRollbackFor = TokenGrantException.class)
    public TokenResponseDto passwordGrant(String username, String password, String scope) {
        log.debug("passwordGrant — username={}", LogMasking.maskEmail(username));
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("passwordGrant — missing username or password");
            throw new InvalidRequestException("username and password are required");
        }

        var userOpt = userRepository.findByEmail(username);
        if (userOpt.isEmpty()) {
            // always run BCrypt to prevent timing-based user enumeration
            passwordEncoder.matches(password, DUMMY_HASH);
            log.warn("passwordGrant — unknown user={}", LogMasking.maskEmail(username));
            throw new InvalidCredentialsException();
        }

        BaseUser user = userOpt.get();

        // reject while the account is locked
        accountLockoutService.assertNotLocked(user);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            accountLockoutService.recordFailedAttempt(user);
            log.warn("passwordGrant — wrong password user={}", LogMasking.maskEmail(username));
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            log.warn("passwordGrant — disabled account user={}", LogMasking.maskEmail(username));
            throw new AccountDisabledException();
        }

        // Successful authentication — reset any lockout state
        accountLockoutService.resetOnSuccess(user);

        // validate requested scope against what this user role is authorized to receive.
        // Prevents scope-escalation attacks (e.g. a regular user requesting scope=admin).
        String grantedScope = scopeResolver.resolveAuthorizedScope(user, scope);

        String accessToken = tokenIssuer.issueAccessToken(user, grantedScope);
        String newRefreshToken = tokenIssuer.createRefreshToken(user, grantedScope);

        log.info(
                "passwordGrant — issued token for user={} userId={} scope={}",
                LogMasking.maskEmail(username),
                user.getId(),
                grantedScope);
        return buildTokenResponse(accessToken, newRefreshToken, grantedScope);
    }

    // -------------------------------------------------------------------------
    // Refresh-token grant
    // -------------------------------------------------------------------------

    /**
     * Handles the refresh-token grant: validates and rotates the refresh token,
     * re-validates the underlying user, and issues a new access/refresh token pair.
     */
    @Transactional(noRollbackFor = TokenGrantException.class)
    public TokenResponseDto refreshGrant(String refreshToken) {
        log.debug("refreshGrant — token=[REDACTED]");
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("refreshGrant — missing refresh_token");
            throw new InvalidRequestException("refresh_token is required");
        }

        // Only accept non-revoked tokens
        var refreshTokenOpt = refreshTokenRepository.findActiveByTokenValue(refreshToken);
        if (refreshTokenOpt.isEmpty()) {
            log.warn("refreshGrant — invalid or already-used refresh token");
            throw new InvalidRefreshTokenException();
        }

        RefreshTokenEntity refreshTokenEntity = refreshTokenOpt.get();
        if (refreshTokenEntity.getExpiresAt().isBefore(Instant.now(clock))) {
            refreshTokenRepository.delete(refreshTokenEntity);
            log.warn("refreshGrant — expired refresh token userId={}", refreshTokenEntity.getUserId());
            throw new ExpiredRefreshTokenException();
        }

        // Re-validate user
        var userOpt = userRepository.findById(refreshTokenEntity.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            refreshTokenRepository.delete(refreshTokenEntity);
            log.warn("refreshGrant — user not found or disabled userId={}", refreshTokenEntity.getUserId());
            throw new RefreshTokenUserInvalidException();
        }

        BaseUser user = userOpt.get();
        String grantedScope = scopeResolver.parsePersistedScope(refreshTokenEntity.getScope());

        // Issue new access token
        String newAccessToken = tokenIssuer.issueAccessToken(user, grantedScope);

        // Rotate: create new refresh token, then revoke the old one
        String newRefreshToken = tokenIssuer.createRefreshToken(user, grantedScope);
        refreshTokenEntity.revoke();
        refreshTokenRepository.save(refreshTokenEntity);

        log.info(
                "refreshGrant — rotated token for user={} userId={}",
                LogMasking.maskEmail(user.getEmail()),
                user.getId());
        return buildTokenResponse(newAccessToken, newRefreshToken, grantedScope);
    }

    /**
     * Builds the token response body shared by both grants, so the field order
     * and {@code token_type}/{@code expires_in} values aren't repeated in two places.
     */
    private TokenResponseDto buildTokenResponse(String accessToken, String refreshToken, String scope) {
        return new TokenResponseDto(
                accessToken, BEARER_TOKEN_TYPE, refreshToken, scope, tokenIssuer.getAccessTokenTtlSeconds());
    }
}
