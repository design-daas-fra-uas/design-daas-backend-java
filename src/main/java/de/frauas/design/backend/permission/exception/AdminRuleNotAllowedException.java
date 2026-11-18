package de.frauas.design.backend.permission.exception;

/**
 * A rule was requested for an admin account. Admins are always allowed by the permission
 * check, so such a rule would never be evaluated; rejecting it keeps the rule list honest.
 *
 * <p>Extends {@link IllegalArgumentException} so it is mapped to {@code 400 Bad Request}
 * by the existing {@code GlobalExceptionHandler} without any additional wiring.</p>
 */
public class AdminRuleNotAllowedException extends IllegalArgumentException {

    public AdminRuleNotAllowedException(Integer adminId) {
        super("Account " + adminId + " is an admin; admins are always allowed and need no rules");
    }
}
