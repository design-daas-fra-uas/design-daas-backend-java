package de.frauas.design.backend.permission.exception;

import de.frauas.design.backend.permission.dto.PermissionRuleKind;

import java.util.NoSuchElementException;

/**
 * No permission rule of the given kind exists with the requested ID.
 *
 * <p>Extends {@link NoSuchElementException} so it is still mapped to {@code 404 Not Found}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class PermissionRuleNotFoundException extends NoSuchElementException {

    public PermissionRuleNotFoundException(PermissionRuleKind kind, Integer id) {
        super("Permission rule not found: " + kind + " " + id);
    }
}
