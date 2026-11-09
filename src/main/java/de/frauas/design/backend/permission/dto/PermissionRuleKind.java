package de.frauas.design.backend.permission.dto;

/**
 * The four kinds of permission rule, in the order {@code PermissionService} checks them.
 * The subject is a user or a user group; the target is one endpoint or an endpoint group.
 */
public enum PermissionRuleKind {
    /** Step 1: user → endpoint. */
    USER_ENDPOINT,
    /** Step 2: user group → endpoint. */
    USER_GROUP_ENDPOINT,
    /** Step 3: user → endpoint group. */
    USER_ENDPOINT_GROUP,
    /** Step 4: user group → endpoint group. */
    USER_GROUP_ENDPOINT_GROUP;

    /** Whether the subject of this kind is a user group (otherwise a single user). */
    public boolean hasGroupSubject() {
        return this == USER_GROUP_ENDPOINT || this == USER_GROUP_ENDPOINT_GROUP;
    }

    /** Whether the target of this kind is an endpoint group (otherwise a single endpoint). */
    public boolean hasGroupTarget() {
        return this == USER_ENDPOINT_GROUP || this == USER_GROUP_ENDPOINT_GROUP;
    }
}
