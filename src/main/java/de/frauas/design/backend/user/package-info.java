/**
 * Accounts: regular users, admins and user groups.
 *
 * <p>Users register themselves and stay disabled until they confirm their e-mail with a
 * one-time code sent by {@link de.frauas.design.backend.user.service.MailService}. Admins are
 * created by other admins (or by the bootstrap initializer) and are enabled immediately.
 * Both share the {@code users} table through single-table inheritance on {@code role}.</p>
 */
package de.frauas.design.backend.user;
