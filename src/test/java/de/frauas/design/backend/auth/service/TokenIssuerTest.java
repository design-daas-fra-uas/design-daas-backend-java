package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.model.AccessTokenEntity;
import de.frauas.design.backend.auth.model.RefreshTokenEntity;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static java.time.Clock.systemUTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenIssuer")
class TokenIssuerTest {

    @Mock
    JwtEncoder jwtEncoder;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    AccessTokenRepository accessTokenRepository;

    @Mock
    Jwt encodedJwt;

    TokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        tokenIssuer = new TokenIssuer(
                jwtEncoder,
                refreshTokenRepository,
                accessTokenRepository,
                systemUTC(),
                "http://localhost:8080",
                3600L,
                30L);
    }

    @Test
    @DisplayName("issueAccessToken encodes a JWT and persists its jti metadata")
    void issueAccessToken_persistsMetadata() {
        when(encodedJwt.getTokenValue()).thenReturn("signed.jwt.token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt);

        String token = tokenIssuer.issueAccessToken(user(), "user");

        ArgumentCaptor<AccessTokenEntity> captor = ArgumentCaptor.forClass(AccessTokenEntity.class);
        verify(accessTokenRepository).save(captor.capture());
        AccessTokenEntity persisted = captor.getValue();
        assertThat(token).isEqualTo("signed.jwt.token");
        assertThat(persisted.getUserId()).isEqualTo(1);
        assertThat(persisted.getScope()).isEqualTo("user");
        assertThat(persisted.getJti()).isNotBlank();
        assertThat(persisted.getIssuedAt()).isNotNull();
        assertThat(persisted.getExpiresAt()).isAfter(persisted.getIssuedAt());
    }

    @Test
    @DisplayName("createRefreshToken generates a random token and persists it with expiry")
    void createRefreshToken_persistsMetadata() {
        String token = tokenIssuer.createRefreshToken(user(), "admin");

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenEntity persisted = captor.getValue();
        assertThat(token).hasSize(64).isEqualTo(persisted.getTokenValue());
        assertThat(persisted.getUserId()).isEqualTo(1);
        assertThat(persisted.getScope()).isEqualTo("admin");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getExpiresAt()).isAfter(persisted.getCreatedAt());
    }

    private User user() {
        User user = new User();
        user.setId(1);
        user.setName("Test User");
        user.setEmail("user@example.com");
        user.setPassword("hash");
        user.setEnabled(true);
        return user;
    }
}
