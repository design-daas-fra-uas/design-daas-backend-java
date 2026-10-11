package de.frauas.design.backend.user.service;

import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.dto.CreateUserRequest;
import de.frauas.design.backend.user.dto.PatchUserRequest;
import de.frauas.design.backend.user.dto.UserDto;
import de.frauas.design.backend.user.exception.AccountNotFoundException;
import de.frauas.design.backend.user.exception.UserNotFoundException;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import de.frauas.design.backend.shared.event.UserSessionsInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core business logic for regular-user registration, verification, and CRUD, plus the
 * application-access request flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** Cryptographically secure random for registration code generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** Character set used to generate one-time email-registration codes. */
    private static final String REGISTRATION_CODE_CHARS = "ABCDEF0123456789";
    /** Length, in characters, of a generated registration code. */
    private static final int REGISTRATION_CODE_LENGTH = 8;
    /** How long a registration code remains valid after account creation. */
    private static final int REGISTRATION_CODE_TIMEOUT_HOURS = 24;

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AccountValidator accountValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Registers a new, disabled user and emails them a registration code that must be
     * confirmed via {@link #validateEmail} before the account can log in.
     *
     * @param request the new user's details
     * @return the created user
     * @throws de.frauas.design.backend.user.exception.WeakPasswordException if the password fails policy checks
     * @throws de.frauas.design.backend.user.exception.EmailAlreadyInUseException if the email is already registered
     */
    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        log.debug("createUser — email={}", LogMasking.maskEmail(request.getEmail()));
        accountValidator.validatePassword(request.getPassword());
        accountValidator.assertEmailAvailable(request.getEmail(), null);
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(false);
        issueRegistrationCode(user);
        if (request.getGroups() != null && !request.getGroups().isEmpty()) {
            user.setGroups(loadGroupsOrThrow(request.getGroups()));
        }
        userRepository.save(user);
        log.info("createUser — user created id={} email={}", user.getId(), LogMasking.maskEmail(user.getEmail()));
        String email = user.getEmail();
        String registrationCode = user.getRegistrationCode();
        runAfterCommit(() -> mailService.sendRegistrationCode(email, registrationCode, REGISTRATION_CODE_TIMEOUT_HOURS));
        return UserDto.from(user);
    }

    /**
     * Confirms a user's email using the one-time code sent by {@link #createUser}, and
     * enables the account on success.
     *
     * @param email the email address to verify
     * @param registrationCode the code supplied by the caller
     * @return {@code true} if the account was found, the code matched and had not
     *     expired (and the account is now enabled); {@code false} otherwise
     */
    @Transactional
    public boolean validateEmail(String email, String registrationCode) {
        log.debug("validateEmail — email={}", LogMasking.maskEmail(email));
        Optional<BaseUser> opt = userRepository.findByEmail(email);
        if (opt.isEmpty() || !(opt.get() instanceof User user)) {
            log.warn("validateEmail — no user found for email={}", LogMasking.maskEmail(email));
            return false;
        }
        if (user.getRegistrationCode() == null || !user.getRegistrationCode().equals(registrationCode)) {
            log.warn("validateEmail — invalid registration code for email={}", LogMasking.maskEmail(email));
            return false;
        }
        if (user.getRegistrationCodeTimeout() != null
                && Instant.now(clock).isAfter(user.getRegistrationCodeTimeout())) {
            log.warn("validateEmail — registration code expired for email={}", LogMasking.maskEmail(email));
            return false;
        }
        user.setEnabled(true);
        user.setRegistrationUsedMoment(Instant.now(clock));
        user.setRegistrationCode(null);
        userRepository.save(user);
        log.info("validateEmail — email verified for userId={} email={}", user.getId(), LogMasking.maskEmail(email));
        return true;
    }

    /**
     * Issues a fresh registration code for a still-unverified account and e-mails it again.
     *
     * <p>Silent by design: unknown, already-verified and admin accounts are ignored so the
     * endpoint cannot be used to find out which e-mail addresses are registered. The
     * previous code is replaced, so only the newest e-mail is valid.</p>
     *
     * @param email the address the original registration was made with
     */
    @Transactional
    public void resendRegistrationCode(String email) {
        log.debug("resendRegistrationCode — email={}", LogMasking.maskEmail(email));
        Optional<BaseUser> opt = userRepository.findByEmail(email);
        if (opt.isEmpty() || !(opt.get() instanceof User user) || user.isEnabled()) {
            log.info("resendRegistrationCode — no pending registration for email={}", LogMasking.maskEmail(email));
            return;
        }
        issueRegistrationCode(user);
        userRepository.save(user);
        String address = user.getEmail();
        String code = user.getRegistrationCode();
        runAfterCommit(() -> mailService.sendRegistrationCode(address, code, REGISTRATION_CODE_TIMEOUT_HOURS));
        log.info("resendRegistrationCode — new code issued userId={}", user.getId());
    }

    /**
     * Returns every regular user, unpaginated.
     *
     * @return all users
     */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        log.debug("getAllUsers — no pagination");
        return userRepository.findAllUsers().stream().map(UserDto::from).toList();
    }

    /**
     * Partially updates a user. Fields left {@code null} on {@code request} are left
     * unchanged.
     *
     * @param id the ID of the user to update
     * @param request the fields to change
     * @return the updated user
     * @throws UserNotFoundException if no user exists with the given id
     * @throws de.frauas.design.backend.user.exception.WeakPasswordException if the new password fails policy checks
     * @throws de.frauas.design.backend.user.exception.EmailAlreadyInUseException if the new email is already used
     *     by a different user
     */
    @Transactional
    public UserDto updateUser(Integer id, PatchUserRequest request) {
        log.debug("updateUser — id={}", id);
        User user = getUserOrThrow(id);
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            accountValidator.assertEmailAvailable(request.getEmail(), id);
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null) {
            accountValidator.validatePassword(request.getPassword());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(id));
        }
        if (request.getGroups() != null) {
            user.setGroups(loadGroupsOrThrow(request.getGroups()));
        }
        userRepository.save(user);
        log.info("updateUser — user updated id={}", id);
        return UserDto.from(user);
    }

    /**
     * Re-enables a previously disabled user account.
     *
     * @param id the ID of the user to enable
     * @return the updated user
     * @throws UserNotFoundException if no user exists with the given id
     */
    @Transactional
    public UserDto enableUser(Integer id) {
        log.debug("enableUser — id={}", id);
        User user = getUserOrThrow(id);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("enableUser — user enabled id={}", id);
        return UserDto.from(user);
    }

    /**
     * Disables a user account, preventing further logins.
     *
     * @param id the ID of the user to disable
     * @return the updated user
     * @throws UserNotFoundException if no user exists with the given id
     */
    @Transactional
    public UserDto disableUser(Integer id) {
        log.debug("disableUser — id={}", id);
        User user = getUserOrThrow(id);
        user.setEnabled(false);
        userRepository.save(user);
        eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(id));
        log.info("disableUser — user disabled id={}", id);
        return UserDto.from(user);
    }

    /**
     * Sends an application-request email on behalf of the authenticated user.
     * Mirrors PHP UserService::requestApplication().
     *
     * @param userId      the ID of the requesting user (from JWT subject)
     * @param application the application name the user wants access to
     * @throws AccountNotFoundException if no account exists with the given id
     */
    @Transactional(readOnly = true)
    public void requestApplication(Integer userId, String application) {
        log.debug("requestApplication — userId={} application={}", userId, application);
        BaseUser baseUser = userRepository.findById(userId).orElseThrow(() -> new AccountNotFoundException(userId));
        String email = baseUser.getEmail();
        String name = baseUser.getName();
        String id = String.valueOf(baseUser.getId());
        runAfterCommit(() -> mailService.sendApplicationRequestEmail(email, name, id, application));
        log.info("requestApplication — email sent for userId={} application={}", userId, application);
    }

    /**
     * Looks up a user by id, or throws if none exists.
     *
     * @param id the ID of the user to look up
     * @return the matching user
     * @throws UserNotFoundException if no user exists with the given id
     */
    private User getUserOrThrow(Integer id) {
        return userRepository.findUserById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private List<UserGroup> loadGroupsOrThrow(List<Integer> groupIds) {
        Set<Integer> requestedGroupIds = new LinkedHashSet<>(groupIds);
        if (requestedGroupIds.isEmpty()) {
            return List.of();
        }
        List<UserGroup> groups = userGroupRepository.findAllById(requestedGroupIds);
        Set<Integer> foundIds = groups.stream().map(UserGroup::getId).collect(Collectors.toSet());
        List<Integer> missingIds =
                requestedGroupIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            log.warn("loadGroupsOrThrow — unknown user group ids={}", missingIds);
            throw new IllegalArgumentException("Unknown user group ids: " + missingIds);
        }
        return groups;
    }

    /**
     * Runs {@code action} once the current transaction commits, so slow I/O (e.g. SMTP
     * sends) never keeps database locks held longer than necessary. Falls back to running
     * immediately if no transaction is active (e.g. during tests).
     *
     * @param action the work to defer until after commit
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void issueRegistrationCode(User user) {
        user.setRegistrationCode(generateRegistrationCode());
        user.setRegistrationCodeTimeout(Instant.now(clock).plus(REGISTRATION_CODE_TIMEOUT_HOURS, ChronoUnit.HOURS));
    }

    private String generateRegistrationCode() {
        StringBuilder sb = new StringBuilder(REGISTRATION_CODE_LENGTH);
        for (int i = 0; i < REGISTRATION_CODE_LENGTH; i++) {
            sb.append(REGISTRATION_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(REGISTRATION_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
