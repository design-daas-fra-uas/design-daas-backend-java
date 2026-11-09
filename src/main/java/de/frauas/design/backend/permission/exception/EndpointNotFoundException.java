package de.frauas.design.backend.permission.exception;

import java.util.NoSuchElementException;

/**
 * No {@code Endpoint} exists with the requested ID.
 *
 * <p>Extends {@link NoSuchElementException} so it is still mapped to {@code 404 Not Found}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class EndpointNotFoundException extends NoSuchElementException {

    public EndpointNotFoundException(Integer id) {
        super("Endpoint not found: " + id);
    }
}
