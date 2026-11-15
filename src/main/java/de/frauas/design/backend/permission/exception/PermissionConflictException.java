package de.frauas.design.backend.permission.exception;

import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * The endpoint, endpoint group or permission rule already exists.
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class PermissionConflictException extends ConflictException {

    public PermissionConflictException(String message) {
        super(message);
    }
}
