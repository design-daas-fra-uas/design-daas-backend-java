package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.dto.GrantRequest;
import de.frauas.design.backend.auth.dto.TokenResponseDto;
import de.frauas.design.backend.auth.exception.AccountDisabledException;
import de.frauas.design.backend.auth.exception.AccountLockedException;
import de.frauas.design.backend.auth.exception.ExpiredRefreshTokenException;
import de.frauas.design.backend.auth.exception.InvalidCredentialsException;
import de.frauas.design.backend.auth.exception.InvalidRefreshTokenException;
import de.frauas.design.backend.auth.exception.InvalidRequestException;
import de.frauas.design.backend.auth.exception.InvalidScopeException;
import de.frauas.design.backend.auth.exception.RefreshTokenUserInvalidException;
import de.frauas.design.backend.auth.model.AccessTokenEntity;
import de.frauas.design.backend.auth.model.RefreshTokenEntity;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static java.time.Clock.systemUTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService")
class TokenServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "correct-password";
    private static final String HASH = "$2a$10$hashedvalue";

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtEncoder jwtEncoder;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    AccessTokenRepository accessTokenRepository;

    @Mock
    Jwt encodedJwt;

    TokenService tokenService;

    @BeforeEach
    void setUp() {
        AccountLockoutService accountLockoutService = new AccountLockoutService(userRepository, systemUTC());
        ScopeResolver scopeResolver = new ScopeResolver();
        TokenIssuer tokenIssuer = new TokenIssuer(
                jwtEncoder,
                refreshTokenRepository,
                accessTokenRepository,
                systemUTC(),
                "http://localhost:8080",
                3600L,
                30L);

        tokenService = new TokenService(
                userRepository,
                passwordEncoder,
                refreshTokenRepository,
                accountLockoutService,
                scopeResolver,
                tokenIssuer,
                systemUTC());
    }

    private User user() {
        User u = new User();
        u.setId(1);
        u.setEmail(EMAIL);
        u.setName("Test User");
        u.setPassword(HASH);
        u.setEnabled(true);
        return u;
    }

    private void stubEncoder() {
        lenient().when(encodedJwt.getTokenValue()).thenReturn("signed.jwt.token");
        lenient().when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt);
    }

    // -------------------------------------------------------------------------
    // Password grant
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("passwordGrant")
    class PasswordGrant {

        @Test
        @DisplayName("throws InvalidRequestException(400) when username or password is missing")
        void missingCredentials_returnsBadRequest() {
            assertThatThrownBy(() -> tokenService.passwordGrant(null, PASSWORD, ""))
                    .isInstanceOf(InvalidRequestException.class)
                    .extracting(ex -> ((InvalidRequestException) ex).getStatus())
                    .isEqualTo(400);
            assertThatThrownBy(() -> tokenService.passwordGrant("   ", PASSWORD, ""))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, "   ", ""))
                    .isInstanceOf(InvalidRequestException.class);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws InvalidCredentialsException(401) and still checks BCrypt (SEC-H4) when user is unknown")
        void unknownUser_returnsUnauthorizedAndRunsDummyHash() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, PASSWORD, ""))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .extracting(ex -> ((InvalidCredentialsException) ex).getStatus())
                    .isEqualTo(401);
            // SEC-H4: BCrypt must still run against the dummy hash to avoid timing-based enumeration
            verify(passwordEncoder).matches(eq(PASSWORD), anyString());
        }

        @Test
        @DisplayName("throws AccountLockedException(429) when the account is locked")
        void lockedAccount_returnsTooManyRequests() {
            User user = user();
            user.setLockedUntil(Instant.now().plusSeconds(60));
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, PASSWORD, ""))
                    .isInstanceOf(AccountLockedException.class)
                    .extracting(ex -> ((AccountLockedException) ex).getStatus())
                    .isEqualTo(429);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("wrong password increments failedLoginAttempts and throws InvalidCredentialsException(401)")
        void wrongPassword_incrementsFailedAttempts() {
            User user = user();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, PASSWORD, ""))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("locks the account after MAX_FAILED_ATTEMPTS consecutive failures")
        void wrongPassword_locksAccountAfterThreshold() {
            User user = user();
            user.setFailedLoginAttempts(4); // one more failure reaches the threshold of 5
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, PASSWORD, ""))
                    .isInstanceOf(InvalidCredentialsException.class);

            assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(user.getLockedUntil()).isNotNull().isAfter(Instant.now());
        }

        @Test
        @DisplayName("throws AccountDisabledException(401) when user is disabled")
        void disabledUser_returnsUnauthorized() {
            User user = user();
            user.setEnabled(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, PASSWORD, ""))
                    .isInstanceOf(AccountDisabledException.class)
                    .extracting(ex -> ((AccountDisabledException) ex).getError())
                    .isEqualTo("invalid_grant");
        }

        @Test
        @DisplayName("throws InvalidScopeException(400) when requested scope exceeds the user's authorized scopes")
        void scopeEscalation_returnsInvalidScope() {
            User user = user();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            assertThatThrownBy(() -> tokenService.passwordGrant(EMAIL, PASSWORD, "admin"))
                    .isInstanceOf(InvalidScopeException.class)
                    .extracting(ex -> ((InvalidScopeException) ex).getError())
                    .isEqualTo("invalid_scope");
        }

        @Test
        @DisplayName("admin user requesting admin scope succeeds")
        void adminScope_allowedForAdminUser() {
            Admin admin = new Admin();
            admin.setId(2);
            admin.setEmail("admin@example.com");
            admin.setName("Admin User");
            admin.setPassword(HASH);
            admin.setEnabled(true);
            stubEncoder();
            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            TokenResponseDto result = tokenService.passwordGrant("admin@example.com", PASSWORD, "admin");

            assertThat(result.scope()).isEqualTo("admin");
            verify(accessTokenRepository).save(any(AccessTokenEntity.class));
            verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
        }

        @Test
        @DisplayName("successful login clears prior lockout state")
        void successfulLogin_resetsLockoutState() {
            User user = user();
            user.setFailedLoginAttempts(3);
            user.setLockedUntil(Instant.now().minusSeconds(1)); // already expired
            stubEncoder();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            tokenService.passwordGrant(EMAIL, PASSWORD, "");

            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("default scope (no scope requested) grants the user's primary role scope")
        void noScopeRequested_defaultsToRoleScope() {
            User user = user();
            stubEncoder();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            TokenResponseDto result = tokenService.passwordGrant(EMAIL, PASSWORD, "");

            assertThat(result.scope()).isEqualTo("user");
        }

        @Test
        @DisplayName("persists the issued access token with its jti and the new refresh token")
        void success_persistsAccessAndRefreshTokens() {
            User user = user();
            stubEncoder();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            ArgumentCaptor<AccessTokenEntity> atCaptor = ArgumentCaptor.forClass(AccessTokenEntity.class);
            tokenService.passwordGrant(EMAIL, PASSWORD, "");

            verify(accessTokenRepository).save(atCaptor.capture());
            assertThat(atCaptor.getValue().getJti()).isNotBlank();
            assertThat(atCaptor.getValue().getUserId()).isEqualTo(1);
            verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
        }
    }

    // -------------------------------------------------------------------------
    // Refresh-token grant
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("refreshGrant")
    class RefreshGrant {

        @Test
        @DisplayName("throws InvalidRequestException(400) when refresh_token is missing")
        void missingToken_returnsBadRequest() {
            assertThatThrownBy(() -> tokenService.refreshGrant(null))
                    .isInstanceOf(InvalidRequestException.class)
                    .extracting(ex -> ((InvalidRequestException) ex).getStatus())
                    .isEqualTo(400);
            assertThatThrownBy(() -> tokenService.refreshGrant("   ")).isInstanceOf(InvalidRequestException.class);
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("throws InvalidRefreshTokenException(401) when refresh token is unknown or already revoked")
        void unknownToken_returnsUnauthorized() {
            when(refreshTokenRepository.findActiveByTokenValue("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tokenService.refreshGrant("bad-token"))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .extracting(ex -> ((InvalidRefreshTokenException) ex).getError())
                    .isEqualTo("invalid_grant");
        }

        @Test
        @DisplayName("deletes and rejects an expired refresh token")
        void expiredToken_isDeletedAndRejected() {
            RefreshTokenEntity rt = new RefreshTokenEntity();
            rt.setTokenValue("expired-token");
            rt.setUserId(1);
            rt.setExpiresAt(Instant.now().minusSeconds(60));
            when(refreshTokenRepository.findActiveByTokenValue("expired-token")).thenReturn(Optional.of(rt));

            assertThatThrownBy(() -> tokenService.refreshGrant("expired-token"))
                    .isInstanceOf(ExpiredRefreshTokenException.class);
            verify(refreshTokenRepository).delete(rt);
        }

        @Test
        @DisplayName("deletes and rejects a token whose user is disabled")
        void disabledUser_isRejected() {
            User user = user();
            user.setEnabled(false);
            RefreshTokenEntity rt = new RefreshTokenEntity();
            rt.setTokenValue("valid-token");
            rt.setUserId(1);
            rt.setExpiresAt(Instant.now().plusSeconds(60));
            when(refreshTokenRepository.findActiveByTokenValue("valid-token")).thenReturn(Optional.of(rt));
            when(userRepository.findById(1)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> tokenService.refreshGrant("valid-token"))
                    .isInstanceOf(RefreshTokenUserInvalidException.class);
            verify(refreshTokenRepository).delete(rt);
        }

        @Test
        @DisplayName("rotates the refresh token and issues a new access token on success")
        void success_rotatesTokenAndIssuesNewAccessToken() {
            User user = user();
            RefreshTokenEntity rt = new RefreshTokenEntity();
            rt.setTokenValue("valid-token");
            rt.setUserId(1);
            rt.setScope("user");
            rt.setExpiresAt(Instant.now().plusSeconds(60));
            stubEncoder();
            when(refreshTokenRepository.findActiveByTokenValue("valid-token")).thenReturn(Optional.of(rt));
            when(userRepository.findById(1)).thenReturn(Optional.of(user));

            TokenResponseDto result = tokenService.refreshGrant("valid-token");

            assertThat(result.refreshToken()).isNotEqualTo("valid-token");
            assertThat(rt.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(rt);
        }
    }

    // -------------------------------------------------------------------------
    // grantToken dispatch
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("grantToken")
    class GrantToken {

        @Test
        @DisplayName("dispatches a PasswordGrantRequest to passwordGrant")
        void passwordGrantRequest_dispatchesToPasswordGrant() {
            User user = user();
            stubEncoder();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            TokenResponseDto result = tokenService.grantToken(GrantRequest.of("password", EMAIL, PASSWORD, null, ""));

            assertThat(result.scope()).isEqualTo("user");
        }

        @Test
        @DisplayName("dispatches a RefreshGrantRequest to refreshGrant")
        void refreshGrantRequest_dispatchesToRefreshGrant() {
            User user = user();
            RefreshTokenEntity rt = new RefreshTokenEntity();
            rt.setTokenValue("valid-token");
            rt.setUserId(1);
            rt.setScope("user");
            rt.setExpiresAt(Instant.now().plusSeconds(60));
            stubEncoder();
            when(refreshTokenRepository.findActiveByTokenValue("valid-token")).thenReturn(Optional.of(rt));
            when(userRepository.findById(1)).thenReturn(Optional.of(user));

            TokenResponseDto result =
                    tokenService.grantToken(GrantRequest.of("refresh_token", null, null, "valid-token", null));

            assertThat(result.refreshToken()).isNotEqualTo("valid-token");
        }

        @Test
        @DisplayName("is transactional so refresh-token locking also works through controller dispatch")
        void grantToken_isTransactional() throws NoSuchMethodException {
            Method method = TokenService.class.getMethod("grantToken", GrantRequest.class);

            assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
        }
    }
}
