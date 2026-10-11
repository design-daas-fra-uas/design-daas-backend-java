package de.frauas.design.backend.desktop.controller;

import de.frauas.design.backend.desktop.dto.DesktopGroupCreateRequest;
import de.frauas.design.backend.desktop.dto.DesktopGroupDto;
import de.frauas.design.backend.desktop.exception.DesktopGroupNotFoundException;
import de.frauas.design.backend.desktop.exception.UserGroupAlreadyAssociatedException;
import de.frauas.design.backend.desktop.service.DesktopService;
import de.frauas.design.backend.shared.security.Authorities;
import de.frauas.design.backend.user.exception.UserGroupNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for desktop-group CRUD and user-group association management.
 * All endpoints require the {@code SCOPE_admin} authority (enforced class-wide).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.IS_ADMIN)
public class DesktopGroupController {

    private final DesktopService desktopService;

    /**
     * Lists all desktop groups.
     *
     * @return 200 OK with every {@link DesktopGroupDto} known to the system
     */
    @GetMapping("/desktop_groups")
    public ResponseEntity<List<DesktopGroupDto>> getAllDesktopGroups() {

        log.info("GET /desktop_groups — listing all desktop groups");

        List<DesktopGroupDto> groups = desktopService.getAllDesktopGroups();

        log.info("GET /desktop_groups — returning {} groups", groups.size());

        return ResponseEntity.ok(groups);
    }

    /**
     * Creates a new desktop group. A duplicate description is rejected with 409 by
     * {@link DesktopService#createDesktopGroup}.
     *
     * @param request the group to create
     * @return 201 Created with no body
     */
    @PostMapping("/desktop_group")
    public ResponseEntity<Void> createDesktopGroup(@Valid @RequestBody DesktopGroupCreateRequest request) {

        log.info("POST /desktop_group — creating desktop group description={}", request.getDescription());

        desktopService.createDesktopGroup(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Associates a user group with a desktop group, granting that user group's members
     * access to every desktop in the desktop group.
     *
     * @param id the desktop group ID
     * @param userGroupId the user group ID
     * @return 201 Created with no body
     * @throws DesktopGroupNotFoundException if no desktop group exists with the given id (404)
     * @throws UserGroupNotFoundException if no user group exists with the given id (404)
     * @throws UserGroupAlreadyAssociatedException if already associated (409)
     */
    @PostMapping("/desktop_group/{id}/user_group/{userGroupId}")
    public ResponseEntity<Void> addUserGroup(@PathVariable Integer id, @PathVariable Integer userGroupId) {

        log.info("POST /desktop_group/{}/user_group/{} — adding user group", id, userGroupId);

        desktopService.addUserGroupToDesktopGroup(id, userGroupId);

        log.info("POST /desktop_group/{}/user_group/{} — association created", id, userGroupId);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
