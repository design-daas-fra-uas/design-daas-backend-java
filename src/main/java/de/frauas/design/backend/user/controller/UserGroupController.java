package de.frauas.design.backend.user.controller;

import de.frauas.design.backend.shared.security.Authorities;
import de.frauas.design.backend.user.dto.UserGroupDto;
import de.frauas.design.backend.user.service.UserGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for user-group CRUD and desktop-group association management.
 * All endpoints require the {@code SCOPE_admin} authority (enforced class-wide).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.IS_ADMIN)
public class UserGroupController {

    private final UserGroupService userGroupService;

    /**
     * Lists all user groups.
     *
     * @return 200 OK with every {@link UserGroupDto} known to the system
     */
    @GetMapping("/user_groups")
    public ResponseEntity<List<UserGroupDto>> getAllUserGroups() {
        log.info("GET /user_groups — listing all user groups");
        List<UserGroupDto> groups = userGroupService.getAllUserGroups();
        log.debug("GET /user_groups — returning {} groups", groups.size());
        return ResponseEntity.ok(groups);
    }

    /**
     * Creates a new user group. Business rules (e.g. the {@code name} falling back to
     * {@code description} when blank, and requiring at least one of the two) are enforced
     * by {@link UserGroupService#createUserGroup}, not here.
     *
     * @param request the group to create
     * @return 201 Created with no body
     */
    @PostMapping("/user_group")
    public ResponseEntity<Void> createUserGroup(@Valid @RequestBody UserGroupDto request) {
        log.info("POST /user_group — creating user group name={}", request.getName());
        UserGroupDto result = userGroupService.createUserGroup(request);
        log.info("POST /user_group — user group created id={} name={}", result.getId(), result.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Partially updates a user group — omitted fields are left unchanged.
     *
     * @param id the ID of the group to update
     * @param request fields to change; {@code null} fields are left untouched, a non-null
     *     {@code userIds} replaces the group's membership entirely
     * @return 200 OK with the updated {@link UserGroupDto}
     */
    @PatchMapping("/user_group/{id}")
    public ResponseEntity<UserGroupDto> updateUserGroup(@PathVariable Integer id, @Valid @RequestBody UserGroupDto request) {
        log.info("PATCH /user_group/{} — updating user group", id);
        UserGroupDto result = userGroupService.updateUserGroup(id, request);
        log.info("PATCH /user_group/{} — updated successfully", id);
        return ResponseEntity.ok(result);
    }
}
