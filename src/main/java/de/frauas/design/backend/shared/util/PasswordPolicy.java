package de.frauas.design.backend.shared.util;

/**
 * Central definition of the password-strength policy used by the validation
 * layers.
 *
 * <p>The policy requires 8 to 72 characters, including one uppercase letter, one
 * lowercase letter, and one digit. The upper bound is BCrypt's 72-byte input limit and
 * also stops oversized payloads being hashed.</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 72;

    /** Human-readable rule shown in validation errors. */
    public static final String MESSAGE =
            "password must be 8-72 characters and contain uppercase, lowercase, and a digit";

    /**
     * 8-72 chars, at least one uppercase, one lowercase, one digit.
     */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,72}$";

    private PasswordPolicy() {}
}
