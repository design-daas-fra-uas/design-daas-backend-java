package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.dto.SessionInfoDto;
import de.frauas.design.backend.auth.exception.MissingTokenException;
import de.frauas.design.backend.auth.exception.TokenRevokedException;
import de.frauas.design.backend.auth.exception.UserDisabledException;
import de.frauas.design.backend.auth.exception.UserNotFoundException;
import de.frauas.design.backend.auth.model.AccessTokenEntity;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService")
class SessionServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    AccessTokenRepository accessTokenRepository;

    SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(userRepository, accessTokenRepository);
    }

    private User user(Integer id, boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@example.com");
        u.setName("User " + id);
        u.setPassword("hash");
        u.setEnabled(enabled);
        return u;
    }

    private Jwt jwt(String jti, Integer subject, Object scopeClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject(String.valueOf(subject))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (jti != null) {
            builder.jti(jti);
        } else {
            builder.claim("no-jti", "placeholder");
        }
        if (scopeClaim != null) {
            builder.claim("scope", scopeClaim);
        }
        return builder.build();
    }

    @Test
    @DisplayName("throws TokenRevokedException (401) when the jti is not persisted (unknown token)")
    void unknownJti_throwsTokenRevoked() {
        Jwt jwt = jwt("unknown-jti", 1, "user");
        when(accessTokenRepository.findByJti("unknown-jti")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession(jwt))
                .isInstanceOf(TokenRevokedException.class)
                .satisfies(ex ->
                        assertThat(((TokenRevokedException) ex).getStatus()).isEqualTo(401));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("throws TokenRevokedException (401) when the access token entity is marked revoked")
    void revokedToken_throwsTokenRevoked() {
        Jwt jwt = jwt("revoked-jti", 1, "user");
        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setJti("revoked-jti");
        entity.revoke();
        when(accessTokenRepository.findByJti("revoked-jti")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> sessionService.getSession(jwt)).isInstanceOf(TokenRevokedException.class);
    }

    @Test
    @DisplayName("throws UserNotFoundException (401) — not 500 — when the token's user no longer exists")
    void missingUser_throwsInvalidTokenNot500() {
        Jwt jwt = jwt("valid-jti", 999, "user");
        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setJti("valid-jti");
        when(accessTokenRepository.findByJti("valid-jti")).thenReturn(Optional.of(entity));
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession(jwt))
                .isInstanceOf(UserNotFoundException.class)
                .satisfies(ex ->
                        assertThat(((UserNotFoundException) ex).getStatus()).isEqualTo(401));
    }

    @Test
    @DisplayName("throws UserDisabledException (401) when the user is disabled")
    void disabledUser_throwsUserDisabled() {
        Jwt jwt = jwt("valid-jti", 1, "user");
        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setJti("valid-jti");
        when(accessTokenRepository.findByJti("valid-jti")).thenReturn(Optional.of(entity));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, false)));

        assertThatThrownBy(() -> sessionService.getSession(jwt)).isInstanceOf(UserDisabledException.class);
    }

    @Test
    @DisplayName("returns session info for a valid, enabled user")
    void validToken_returnsSessionInfoDto() {
        Jwt jwt = jwt("valid-jti", 1, "user admin");
        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setJti("valid-jti");
        when(accessTokenRepository.findByJti("valid-jti")).thenReturn(Optional.of(entity));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, true)));

        SessionInfoDto result = sessionService.getSession(jwt);

        assertThat(result.userId()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("User 1");
        assertThat(result.role()).isEqualTo("user");
    }

    @Test
    @DisplayName("treats a token without a jti claim as revoked")
    void missingJtiClaim_treatedAsRevoked() {
        Jwt jwt = jwt(null, 1, "user");

        assertThatThrownBy(() -> sessionService.getSession(jwt)).isInstanceOf(TokenRevokedException.class);
        verifyNoInteractions(accessTokenRepository);
    }

    @Test
    @DisplayName("throws MissingTokenException (401) — not NullPointerException — when jwt is null")
    void nullJwt_throwsMissingToken() {
        assertThatThrownBy(() -> sessionService.getSession(null))
                .isInstanceOf(MissingTokenException.class)
                .satisfies(ex ->
                        assertThat(((MissingTokenException) ex).getStatus()).isEqualTo(401));
        verifyNoInteractions(accessTokenRepository, userRepository);
    }

    @Test
    @DisplayName(
            "throws UserNotFoundException (401) when the subject claim is blank instead of bubbling a parsing failure")
    void blankSubject_throwsUserNotFound() {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .jti("valid-jti")
                .subject(" ")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        AccessTokenEntity entity = new AccessTokenEntity();
        entity.setJti("valid-jti");
        when(accessTokenRepository.findByJti("valid-jti")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> sessionService.getSession(jwt)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(userRepository);
    }
}
