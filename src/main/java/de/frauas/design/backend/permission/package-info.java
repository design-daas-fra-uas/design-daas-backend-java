/**
 * Permission checks for DESIGN DaaS functions.
 *
 * <p>{@link de.frauas.design.backend.permission.service.PermissionService} answers whether a
 * user may call a function using a 4-step cascade (user rule, group rule, user endpoint-group
 * rule, group endpoint-group rule). The first step with a match decides, an explicit deny wins
 * within a step, and the default is deny. Rules are maintained directly in the database.</p>
 */
package de.frauas.design.backend.permission;
