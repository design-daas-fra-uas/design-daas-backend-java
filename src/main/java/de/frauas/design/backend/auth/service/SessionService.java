package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.dto.SessionInfoDto;
import de.frauas.design.backend.auth.exception.MissingTokenException;
import de.frauas.design.backend.auth.exception.TokenRevokedException;
import de.frauas.design.backend.auth.exception.UserDisabledException;
import de.frauas.design.backend.auth.exception.UserNotFoundException;
import de.frauas.design.backend.auth.model.AccessTokenEntity;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Handles {@code GET /oauth2/user/session}: checks that the given JWT is still valid
 * (not revoked) and that the user behind it still exists and is enabled, then returns
 * a short summary of the session.
 *
 * <p><b>How failures work:</b> a bad session (no token, revoked token, unknown or
 * disabled user) is a normal, expected result here — not a bug. So each case throws its
 * own small exception ({@link MissingTokenException}, {@link TokenRevokedException},
 * {@link UserNotFoundException}, {@link UserDisabledException}), and {@code
 * SessionExceptionHandler} turns that into the right HTTP response. Any other, unexpected
 * exception still becomes a 500, as normal.</p>
 *
 * <p>Note: this endpoint is just an extra check for the caller, not the main security
 * gate. The real revocation check for all other endpoints happens earlier, in {@code
 * TokenRevocationValidator}. So by the time this code runs, the token has usually already
 * been checked once — this just repeats the check to give a clearer error message.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserRepository userRepository;
    private final AccessTokenRepository accessTokenRepository;

    /**
     * Validates the given JWT and returns the session summary.
     *
     * @throws MissingTokenException if {@code jwt} is {@code null}
     * @throws TokenRevokedException if the token is unknown or revoked
     * @throws UserNotFoundException if the token's user no longer exists
     * @throws UserDisabledException if the token's user is disabled
     */
    public SessionInfoDto getSession(Jwt jwt) {
        if (jwt == null) {
            log.warn("getSession — no JWT present");
            throw new MissingTokenException();
        }

        String jti = jwt.getId();
        log.debug("getSession — validating token jti={} sub={}", jti, jwt.getSubject());

        // A missing jti is treated the same as an unknown/revoked token — every token issued
        // by TokenService always carries one, so its absence indicates a malformed or foreign token.
        boolean revoked = jti == null
                || accessTokenRepository
                        .findByJti(jti)
                        .map(AccessTokenEntity::isRevoked)
                        .orElse(true);
        if (revoked) {
            log.warn("getSession — token revoked or unknown jti={}", jti);
            throw new TokenRevokedException();
        }

        Integer userId = parseUserId(jwt.getSubject());
        if (userId == null) {
            log.warn("getSession — missing or non-numeric token subject={}", jwt.getSubject());
            throw new UserNotFoundException();
        }
        BaseUser user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("getSession — no user found for token subject userId={}", userId);
            return new UserNotFoundException();
        });

        if (!user.isEnabled()) {
            log.warn("getSession — user disabled userId={}", userId);
            throw new UserDisabledException();
        }

        String scope = parseScopeClaim(jwt);
        log.info("getSession — session valid userId={} role={} scope={}", userId, user.getRole(), scope);
        return new SessionInfoDto(userId, user.getName(), user.getRole());
    }

    /**
     * Parses the JWT's {@code scope} claim (a single scope value, since each user has
     * exactly one role).
     */
    private String parseScopeClaim(Jwt jwt) {
        Object scopeClaim = jwt.getClaim("scope");
        return scopeClaim == null ? null : scopeClaim.toString();
    }

    private Integer parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
