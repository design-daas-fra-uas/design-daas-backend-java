package de.frauas.design.backend.user.exception;

import de.frauas.design.backend.shared.util.PasswordPolicy;

/**
 * The supplied password does not meet the strength policy (8-72 characters, at
 * least one uppercase letter, one lowercase letter, and one digit).
 *
 * <p>Extends {@link IllegalArgumentException} so it is still mapped to {@code 400 Bad Request}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class WeakPasswordException extends IllegalArgumentException {

    public WeakPasswordException() {
        super(PasswordPolicy.MESSAGE);
    }
}
