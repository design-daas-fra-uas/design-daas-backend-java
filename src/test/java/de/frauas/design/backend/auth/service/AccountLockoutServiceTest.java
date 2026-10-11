package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.exception.AccountLockedException;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static java.time.Clock.systemUTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLockoutService")
class AccountLockoutServiceTest {

    @Mock
    UserRepository userRepository;

    AccountLockoutService accountLockoutService;

    @BeforeEach
    void setUp() {
        accountLockoutService = new AccountLockoutService(userRepository, systemUTC());
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

    @Test
    @DisplayName("assertNotLocked is a no-op for unlocked users")
    void assertNotLocked_unlockedUser_doesNotThrow() {
        assertThatCode(() -> accountLockoutService.assertNotLocked(user())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertNotLocked throws when the lock window is still active")
    void assertNotLocked_lockedUser_throws() {
        User user = user();
        user.setLockedUntil(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> accountLockoutService.assertNotLocked(user))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("recordFailedAttempt increments attempts and persists the user")
    void recordFailedAttempt_incrementsAndSaves() {
        User user = user();

        accountLockoutService.recordFailedAttempt(user);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("recordFailedAttempt locks the user on the fifth failure")
    void recordFailedAttempt_thresholdReached_setsLockedUntil() {
        User user = user();
        user.setFailedLoginAttempts(4);

        accountLockoutService.recordFailedAttempt(user);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetOnSuccess clears lockout state and persists it")
    void resetOnSuccess_dirtyState_resetsAndSaves() {
        User user = user();
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(Instant.now().plusSeconds(60));

        accountLockoutService.resetOnSuccess(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetOnSuccess does nothing when there is no lockout state to clear")
    void resetOnSuccess_cleanState_isNoOp() {
        accountLockoutService.resetOnSuccess(user());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
