package de.frauas.design.backend.permission.exception;

import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * An {@code EndpointGroup} with the same name already exists.
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class EndpointGroupAlreadyExistsException extends ConflictException {

    public EndpointGroupAlreadyExistsException(String name) {
        super("An endpoint group '" + name + "' already exists");
    }
}
