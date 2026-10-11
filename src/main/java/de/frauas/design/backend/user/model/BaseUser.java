package de.frauas.design.backend.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Base persistence entity for both regular {@link User}s and {@link Admin}s, mapped
 * with single-table inheritance (discriminated by the {@code role} column).
 * Holds shared credentials, registration/verification state, and account-lockout
 * bookkeeping (failed login attempts / lock expiry).
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "password")
public abstract class BaseUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "registration_code")
    private String registrationCode;

    @Column(name = "registration_code_timeout")
    private Instant registrationCodeTimeout;

    @Column(name = "registration_used_moment")
    private Instant registrationUsedMoment;

    /** consecutive failed password attempts since last successful login. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** if non-null and in the future, the account is temporarily locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    public abstract String getRole();
}
