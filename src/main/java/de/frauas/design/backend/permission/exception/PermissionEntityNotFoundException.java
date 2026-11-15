package de.frauas.design.backend.permission.exception;

import java.util.NoSuchElementException;

/**
 * An endpoint, endpoint group, permission rule, or a rule's user / user group does not exist.
 *
 * <p>Extends {@link NoSuchElementException} so it is still mapped to {@code 404 Not Found}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class PermissionEntityNotFoundException extends NoSuchElementException {

    public PermissionEntityNotFoundException(String entity, Integer id) {
        super(entity + " not found: " + id);
    }
}
