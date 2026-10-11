package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenCleanupService")
class TokenCleanupServiceTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    AccessTokenRepository accessTokenRepository;

    TokenCleanupService tokenCleanupService;

    @BeforeEach
    void setUp() {
        tokenCleanupService =
                new TokenCleanupService(refreshTokenRepository, accessTokenRepository, java.time.Clock.systemUTC());
    }

    @Test
    @DisplayName("purges expired refresh and access tokens using the same cutoff instant")
    void purgeExpiredTokens_deletesFromBothRepositoriesWithSameCutoff() {
        Instant before = Instant.now();

        tokenCleanupService.purgeExpiredTokens();

        Instant after = Instant.now();

        ArgumentCaptor<Instant> refreshCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> accessCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpired(refreshCutoff.capture());
        verify(accessTokenRepository).deleteExpired(accessCutoff.capture());

        assertThat(refreshCutoff.getValue()).isBetween(before, after);
        assertThat(accessCutoff.getValue()).isBetween(before, after);
    }
}
