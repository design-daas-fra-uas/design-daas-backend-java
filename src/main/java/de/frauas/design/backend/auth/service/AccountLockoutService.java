package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.exception.AccountLockedException;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Encapsulates the account-lockout policy applied during the password grant:
 * consecutive failed login attempts are counted, and the account is
 * temporarily locked once too many failures occur in a row. Extracted from
 * {@link TokenService} so this policy can be unit-tested in isolation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountLockoutService {

    /**
     * Lock the account after this many consecutive failed login attempts.
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * Duration of the temporary account lock after too many failures.
     */
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Throws {@link AccountLockedException} if {@code user} is currently within
     * its lockout window.
     */
    public void assertNotLocked(BaseUser user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now(clock))) {
            log.warn(
                    "assertNotLocked — login attempt on locked account user={}", LogMasking.maskEmail(user.getEmail()));
            throw new AccountLockedException();
        }
    }

    /**
     * Increments the failed-login counter and locks the account once
     * {@link #MAX_FAILED_ATTEMPTS} is reached.
     *
     * <p>Note: the counter is only cleared by {@link #resetOnSuccess}, not by the
     * lockout window expiring. So if the account is locked, the lock expires, and
     * the very next attempt fails again, the account is immediately re-locked for
     * another full {@link #LOCKOUT_DURATION} — a single post-expiry failure is
     * enough to re-trigger the lock, since the counter was never reset back down.</p>
     */
    public void recordFailedAttempt(BaseUser user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now(clock).plus(LOCKOUT_DURATION));
            log.warn(
                    "recordFailedAttempt — account locked after {} failed attempts user={}",
                    attempts,
                    LogMasking.maskEmail(user.getEmail()));
        }
        userRepository.save(user);
    }

    /**
     * Clears any prior lockout state after a successful authentication.
     */
    public void resetOnSuccess(BaseUser user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}
