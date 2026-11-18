package de.frauas.design.backend.permission.exception;

import java.util.NoSuchElementException;

/**
 * No {@code EndpointGroup} exists with the requested ID.
 *
 * <p>Extends {@link NoSuchElementException} so it is still mapped to {@code 404 Not Found}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class EndpointGroupNotFoundException extends NoSuchElementException {

    public EndpointGroupNotFoundException(Integer id) {
        super("EndpointGroup not found: " + id);
    }
}
