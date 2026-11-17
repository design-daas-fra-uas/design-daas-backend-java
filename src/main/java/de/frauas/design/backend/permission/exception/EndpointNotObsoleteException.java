package de.frauas.design.backend.permission.exception;

import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * Only obsolete endpoints may be deleted: design-daas still has the route, so the next sync
 * would add it again (without its rules).
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class EndpointNotObsoleteException extends ConflictException {

    public EndpointNotObsoleteException(String functionName) {
        super("The endpoint '" + functionName + "' still exists in design-daas and cannot be deleted");
    }
}
