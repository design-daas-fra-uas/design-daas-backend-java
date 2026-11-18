package de.frauas.design.backend.permission.exception;

import de.frauas.design.backend.permission.dto.PermissionRuleKind;
import de.frauas.design.backend.shared.exception.ConflictException;

/**
 * A rule of the same kind for the same subject and target already exists. Change it by
 * deleting it and creating a new one.
 *
 * <p>Extends {@link ConflictException} so it is mapped to {@code 409 Conflict}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class PermissionRuleAlreadyExistsException extends ConflictException {

    public PermissionRuleAlreadyExistsException(PermissionRuleKind kind, Integer subjectId, Integer targetId) {
        super("A " + kind + " rule for subject " + subjectId + " and target " + targetId + " already exists");
    }
}
