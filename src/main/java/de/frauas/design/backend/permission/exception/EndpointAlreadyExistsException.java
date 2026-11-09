package de.frauas.design.backend.permission.exception;

import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * An {@code Endpoint} with the same function name already exists.
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class EndpointAlreadyExistsException extends ConflictException {

    public EndpointAlreadyExistsException(String functionName) {
        super("An endpoint '" + functionName + "' already exists");
    }
}
