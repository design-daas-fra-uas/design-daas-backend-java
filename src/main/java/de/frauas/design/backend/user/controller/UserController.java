package de.frauas.design.backend.user.controller;

import de.frauas.design.backend.auth.service.JwtUserResolver;
import de.frauas.design.backend.shared.security.Authorities;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.dto.CreateUserRequest;
import de.frauas.design.backend.user.dto.PatchUserRequest;
import de.frauas.design.backend.user.dto.UserDto;
import de.frauas.design.backend.user.dto.ValidateEmailRequest;
import de.frauas.design.backend.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for self-service user registration/verification, admin-managed
 * user CRUD, and application-access requests initiated by authenticated users.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUserResolver jwtUserResolver;

    /**
     * Registers a new (unverified) user. The account remains disabled until
     * {@link #validateEmail} succeeds.
     *
     * @param request the new user's details
     * @return 200 OK with the created {@link UserDto}
     */
    @PostMapping("/user")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("POST /user — registering new user email={}", LogMasking.maskEmail(request.getEmail()));
        UserDto result = userService.createUser(request);
        log.info("POST /user — user created id={} email={}", result.getId(), LogMasking.maskEmail(result.getEmail()));
        return ResponseEntity.ok(result);
    }

    /**
     * Verifies a newly-registered user's email using the one-time registration code
     * sent to them, enabling their account on success.
     *
     * @param request the email and registration code to verify
     * @return 200 OK if verified, 400 Bad Request if the code is missing/incorrect/expired
     */
    @PostMapping("/user/validate_email")
    public ResponseEntity<Void> validateEmail(@Valid @RequestBody ValidateEmailRequest request) {
        log.info("POST /user/validate_email — email={}", LogMasking.maskEmail(request.getEmail()));
        boolean ok = userService.validateEmail(request.getEmail(), request.getRegistrationCode());
        if (ok) {
            log.info(
                    "POST /user/validate_email — email verified successfully email={}",
                    LogMasking.maskEmail(request.getEmail()));
        } else {
            log.warn(
                    "POST /user/validate_email — verification failed email={}",
                    LogMasking.maskEmail(request.getEmail()));
        }
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    /**
     * GET /users — list users.
     *
     * @return 200 OK with the matching {@link UserDto} list
     */
    @GetMapping("/users")
    @PreAuthorize(Authorities.IS_ADMIN)
    public ResponseEntity<List<UserDto>> getAllUsers() {
        log.info("GET /users — listing all users");
        List<UserDto> users = userService.getAllUsers();
        log.debug("GET /users — returning {} users (no pagination)", users.size());
        return ResponseEntity.ok(users);
    }

    /**
     * Partially updates a user (admin-only). Fields omitted from {@code request} are
     * left unchanged.
     *
     * @param userId the ID of the user to update
     * @param request the fields to change
     * @return 200 OK with the updated {@link UserDto}
     */
    @PatchMapping("/user/{userId}")
    @PreAuthorize(Authorities.IS_ADMIN)
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Integer userId, @Valid @RequestBody PatchUserRequest request) {
        log.info("PATCH /user/{} — updating user", userId);
        UserDto result = userService.updateUser(userId, request);
        log.info("PATCH /user/{} — user updated successfully", userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Re-enables a previously disabled user account (admin-only).
     *
     * @param userId the ID of the user to enable
     * @return 200 OK with the updated {@link UserDto}
     */
    @PostMapping("/user/{userId}/enable")
    @PreAuthorize(Authorities.IS_ADMIN)
    public ResponseEntity<UserDto> enableUser(@PathVariable Integer userId) {
        log.info("POST /user/{}/enable — enabling user", userId);
        UserDto result = userService.enableUser(userId);
        log.info("POST /user/{}/enable — user enabled", userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Disables a user account, preventing further logins (admin-only).
     *
     * @param userId the ID of the user to disable
     * @return 200 OK with the updated {@link UserDto}
     */
    @PostMapping("/user/{userId}/disable")
    @PreAuthorize(Authorities.IS_ADMIN)
    public ResponseEntity<UserDto> disableUser(@PathVariable Integer userId) {
        log.info("POST /user/{}/disable — disabling user", userId);
        UserDto result = userService.disableUser(userId);
        log.info("POST /user/{}/disable — user disabled", userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Requests access to an application on behalf of the currently authenticated user,
     * triggering a notification email to the administrators.
     *
     * @param application the application identifier being requested
     * @param isoCode the ISO country/locale code associated with the request
     * @param jwt the caller's authenticated JWT, used to resolve the requesting user ID
     * @return 200 OK once the request has been recorded and the notification sent
     */
    @PostMapping("/user/request-application/{application}/{isoCode}")
    @PreAuthorize(Authorities.IS_ANY_USER)
    public ResponseEntity<Void> requestApplication(
            @PathVariable @Pattern(regexp = "[\\w .-]{1,100}", message = "invalid application name") String application,
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,10}", message = "invalid ISO code") String isoCode,
            @AuthenticationPrincipal Jwt jwt) {
        Integer userId = jwtUserResolver.resolveFromAuthenticatedJwt(jwt);
        log.info("POST /user/request-application/{}/{} — userId={}", application, isoCode, userId);
        userService.requestApplication(userId, application);
        log.info(
                "POST /user/request-application — application request email sent userId={} app={}",
                userId,
                application);
        return ResponseEntity.ok().build();
    }
}
