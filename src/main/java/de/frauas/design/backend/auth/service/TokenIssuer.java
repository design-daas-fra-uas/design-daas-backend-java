package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.model.AccessTokenEntity;
import de.frauas.design.backend.auth.model.RefreshTokenEntity;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.user.model.BaseUser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Builds and persists access tokens (signed JWTs) and refresh tokens (opaque
 * random strings).
 *
 * <p>The access token's JWT payload includes the user's email and name as
 * claims, so anywhere the token is logged or stored should be treated as
 * containing personal data.</p>
 */
@Component
@Slf4j
public class TokenIssuer {

    /**
     * Cryptographically secure random for refresh token generation. {@link SecureRandom}
     * is thread-safe, so sharing one static instance across all requests is safe and
     * avoids the cost of re-seeding a new instance per call.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final Clock clock;

    /**
     * The {@code iss} claim to embed in every issued access token.
     */
    private final String issuer;

    /**
     * How long a refresh token stays valid after creation, in days.
     */
    private final long refreshTokenTtlDays;

    /**
     * How long an access token stays valid after creation, in seconds. Exposed via
     * a getter so {@link TokenService} can report the same value as the
     * {@code expires_in} field of the token response, without duplicating the
     * config lookup.
     */
    @Getter
    private final long accessTokenTtlSeconds;

    /**
     * All configuration is constructor-injected (rather than field-injected via
     * {@code @Value} on non-final fields) so that {@code issuer}/TTLs are immutable
     * and this class can be constructed directly in unit tests without reflection.
     */
    public TokenIssuer(
            JwtEncoder jwtEncoder,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenRepository accessTokenRepository,
            Clock clock,
            @Value("${app.oauth2.issuer:http://localhost:8080}") String issuer,
            @Value("${app.oauth2.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds,
            @Value("${app.oauth2.refresh-token-ttl-days:30}") long refreshTokenTtlDays) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.clock = clock;
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    /**
     * Builds a JWT with a unique {@code jti} claim, persists it to the
     * {@code access_tokens} table, and returns the compact token string.
     */
    public String issueAccessToken(BaseUser user, String scope) {
        Instant now = Instant.now(clock);
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("scope", scope)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String tokenValue =
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        // Persist for revocation support
        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setJti(jti);
        entity.setUserId(user.getId());
        entity.setScope(scope);
        entity.setIssuedAt(now);
        entity.setExpiresAt(now.plusSeconds(accessTokenTtlSeconds));
        accessTokenRepository.save(entity);
        log.debug("issueAccessToken — issued jti={} userId={}", jti, user.getId());

        return tokenValue;
    }

    /**
     * Generates a cryptographically secure refresh token (256-bit hex),
     * persists it, and returns the token string.
     */
    public String createRefreshToken(BaseUser user, String scope) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String tokenValue = HexFormat.of().formatHex(bytes);

        Instant now = Instant.now(clock);
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setTokenValue(tokenValue);
        rt.setUserId(user.getId());
        rt.setScope(scope);
        rt.setCreatedAt(now);
        rt.setExpiresAt(now.plus(refreshTokenTtlDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(rt);
        log.debug("createRefreshToken — issued userId={}", user.getId());
        return tokenValue;
    }
}
