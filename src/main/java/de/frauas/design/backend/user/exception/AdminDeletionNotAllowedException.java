package de.frauas.design.backend.user.exception;

import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * An admin may not be deleted: either the caller tried to delete their own account, or it
 * is the last remaining admin (deleting it would leave nobody able to manage the system).
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class AdminDeletionNotAllowedException extends ConflictException {

    public AdminDeletionNotAllowedException(String message) {
        super(message);
    }
}
