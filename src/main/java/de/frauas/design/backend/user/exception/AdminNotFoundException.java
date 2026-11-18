package de.frauas.design.backend.user.exception;

import java.util.NoSuchElementException;

/**
 * No {@code Admin} exists with the requested ID.
 *
 * <p>Extends {@link NoSuchElementException} so it is still mapped to {@code 404 Not Found}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class AdminNotFoundException extends NoSuchElementException {

    public AdminNotFoundException(Integer id) {
        super("Admin not found: " + id);
    }
}
