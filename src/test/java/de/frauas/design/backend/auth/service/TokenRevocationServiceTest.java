package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.model.AccessTokenEntity;
import de.frauas.design.backend.auth.model.RefreshTokenEntity;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.shared.event.UserSessionsInvalidatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenRevocationService")
class TokenRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Mock
    AccessTokenRepository accessTokenRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(
                accessTokenRepository, refreshTokenRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RefreshTokenEntity refreshToken(int userId) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUserId(userId);
        return token;
    }

    @Test
    @DisplayName("logout revokes the access token and the caller's refresh token")
    void logoutRevokesBoth() {
        AccessTokenEntity access = new AccessTokenEntity();
        RefreshTokenEntity refresh = refreshToken(7);
        when(accessTokenRepository.findByJti("jti")).thenReturn(Optional.of(access));
        when(refreshTokenRepository.findActiveByTokenValue("rt")).thenReturn(Optional.of(refresh));

        service.logout(7, "jti", "rt");

        assertThat(access.isRevoked()).isTrue();
        assertThat(refresh.isRevoked()).isTrue();
        verify(accessTokenRepository).save(access);
        verify(refreshTokenRepository).save(refresh);
    }

    @Test
    @DisplayName("logout never revokes another user's refresh token")
    void logoutKeepsForeignRefreshToken() {
        RefreshTokenEntity foreign = refreshToken(8);
        when(refreshTokenRepository.findActiveByTokenValue("rt")).thenReturn(Optional.of(foreign));

        service.logout(7, null, "rt");

        assertThat(foreign.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any());
        verifyNoInteractions(accessTokenRepository);
    }

    @Test
    @DisplayName("logout ignores unknown tokens and a blank refresh token")
    void logoutIgnoresUnknown() {
        when(accessTokenRepository.findByJti("unknown")).thenReturn(Optional.empty());

        service.logout(7, "unknown", " ");
        service.logout(7, null, null);

        verify(accessTokenRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("revokeAllForUser revokes both token types at the clock's time")
    void revokeAll() {
        when(accessTokenRepository.revokeAllByUserId(7, NOW)).thenReturn(2);
        when(refreshTokenRepository.revokeAllByUserId(7, NOW)).thenReturn(1);

        service.revokeAllForUser(7);

        verify(accessTokenRepository).revokeAllByUserId(7, NOW);
        verify(refreshTokenRepository).revokeAllByUserId(7, NOW);
    }

    @Test
    @DisplayName("an invalidated-sessions event revokes all of the user's tokens")
    void onEvent() {
        service.onUserSessionsInvalidated(new UserSessionsInvalidatedEvent(9));

        verify(accessTokenRepository).revokeAllByUserId(9, NOW);
        verify(refreshTokenRepository).revokeAllByUserId(9, NOW);
    }
}
