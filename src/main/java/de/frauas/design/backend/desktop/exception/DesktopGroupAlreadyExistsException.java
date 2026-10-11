package de.frauas.design.backend.desktop.exception;

import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * A {@code DesktopGroup} with the same name already exists.
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class DesktopGroupAlreadyExistsException extends ConflictException {

    public DesktopGroupAlreadyExistsException(String name) {
        super("A desktop group named '" + name + "' already exists");
    }
}
