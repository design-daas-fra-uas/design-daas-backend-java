/**
 * Permission checks for DESIGN DaaS functions.
 *
 * <p>{@link de.frauas.design.backend.permission.service.PermissionService} answers whether a
 * user may call a function using a 4-step cascade (user rule, group rule, user endpoint-group
 * rule, group endpoint-group rule). The first step with a match decides, an explicit deny wins
 * within a step, and the default is deny. Admins manage endpoints, endpoint groups and rules
 * through {@link de.frauas.design.backend.permission.service.PermissionManagementService}.</p>
 */
package de.frauas.design.backend.permission;
