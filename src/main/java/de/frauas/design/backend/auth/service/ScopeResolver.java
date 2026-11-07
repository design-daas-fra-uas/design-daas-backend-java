package de.frauas.design.backend.auth.service;

import de.frauas.design.backend.auth.exception.InvalidScopeException;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.model.BaseUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves and validates the OAuth2 scope for a user, ensuring clients
 * cannot escalate beyond the scope their role is authorized to receive.
 * Extracted from {@link TokenService} so scope rules can be unit-tested
 * independently of grant handling.
 *
 * <p>Each user has exactly one role, and that role is also their only valid scope.</p>
 */
@Component
@Slf4j
public class ScopeResolver {

    /**
     * Returns the scope to grant for this user's role, checking that the client
     * didn't request a different one.
     *
     * <p>Each role has exactly one scope (its own name: {@code admin}
     * or {@code user}) — there's no downgrading to a lower scope. If the client
     * requests no scope, the user's role is returned. If the client requests any
     * scope other than their own role, an {@link InvalidScopeException} is thrown.</p>
     *
     * @param user           the authenticated user
     * @param requestedScope the raw scope parameter from the token request (may be null/blank)
     * @return the user's role, used as the granted scope
     * @throws InvalidScopeException if the requested scope doesn't match the user's role
     */
    public String resolveAuthorizedScope(BaseUser user, String requestedScope) {
        String role = user.getRole();
        if (requestedScope == null || requestedScope.isBlank()) {
            return role;
        }

        String requested = requestedScope.trim();
        if (!requested.equals(role)) {
            log.warn(
                    "resolveAuthorizedScope — scope escalation attempt by user={}: requested={}, role={}",
                    LogMasking.maskEmail(user.getEmail()),
                    requested,
                    role);
            throw new InvalidScopeException();
        }

        return role;
    }

    /**
     * Resolves a scope string that was previously persisted to the database (e.g.
     * the {@code scope} column of a refresh token). Because this value was already
     * validated at issuance time, no role-check is needed here.
     *
     * <p>A blank/missing value is unexpected (every scope should have been set at
     * issuance) and is downgraded to the least-privileged {@code user} scope rather
     * than failing the request; a warning is logged so the underlying data issue
     * doesn't go unnoticed.</p>
     */
    public String parsePersistedScope(String scope) {
        if (scope == null || scope.isBlank()) {
            log.warn("parsePersistedScope — blank/missing persisted scope, defaulting to 'user'");
            return "user";
        }
        return scope.trim();
    }
}
