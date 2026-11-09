package de.frauas.design.backend.permission.controller;

import de.frauas.design.backend.permission.dto.EndpointCreateRequest;
import de.frauas.design.backend.permission.dto.EndpointDto;
import de.frauas.design.backend.permission.dto.EndpointGroupCreateRequest;
import de.frauas.design.backend.permission.dto.EndpointGroupDto;
import de.frauas.design.backend.permission.dto.EndpointGroupPatchRequest;
import de.frauas.design.backend.permission.dto.PermissionRuleCreateRequest;
import de.frauas.design.backend.permission.dto.PermissionRuleDto;
import de.frauas.design.backend.permission.dto.PermissionRuleKind;
import de.frauas.design.backend.permission.service.PermissionManagementService;
import de.frauas.design.backend.shared.security.Authorities;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for managing the permission data that {@link PermissionController}
 * evaluates: endpoints, endpoint groups and allow/deny rules. All endpoints require the
 * {@code SCOPE_admin} authority (enforced class-wide).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.IS_ADMIN)
public class PermissionManagementController {

    private final PermissionManagementService permissionManagementService;

    /**
     * Lists all endpoints.
     *
     * @return 200 OK with every {@link EndpointDto} known to the system
     */
    @GetMapping("/endpoints")
    public ResponseEntity<List<EndpointDto>> getAllEndpoints() {

        log.info("GET /endpoints — listing all endpoints");

        List<EndpointDto> endpoints = permissionManagementService.getAllEndpoints();

        log.debug("GET /endpoints — returning {} endpoints", endpoints.size());

        return ResponseEntity.ok(endpoints);
    }

    /**
     * Registers a design-daas function so rules can target it. A duplicate function name is
     * rejected with 409 by {@link PermissionManagementService#createEndpoint}.
     *
     * @param request the endpoint to create
     * @return 201 Created with no body
     */
    @PostMapping("/endpoint")
    public ResponseEntity<Void> createEndpoint(@Valid @RequestBody EndpointCreateRequest request) {

        log.info("POST /endpoint — creating endpoint functionName={}", request.getFunctionName());

        permissionManagementService.createEndpoint(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Deletes an endpoint together with every rule targeting it.
     *
     * @param id the ID of the endpoint to delete
     * @return 204 No Content
     * @throws de.frauas.design.backend.permission.exception.EndpointNotFoundException if no
     *     endpoint exists with the given id (404)
     */
    @DeleteMapping("/endpoint/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Integer id) {

        log.info("DELETE /endpoint/{} — deleting endpoint", id);

        permissionManagementService.deleteEndpoint(id);

        log.info("DELETE /endpoint/{} — endpoint deleted", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all endpoint groups.
     *
     * @return 200 OK with every {@link EndpointGroupDto} known to the system
     */
    @GetMapping("/endpoint_groups")
    public ResponseEntity<List<EndpointGroupDto>> getAllEndpointGroups() {

        log.info("GET /endpoint_groups — listing all endpoint groups");

        List<EndpointGroupDto> groups = permissionManagementService.getAllEndpointGroups();

        log.debug("GET /endpoint_groups — returning {} groups", groups.size());

        return ResponseEntity.ok(groups);
    }

    /**
     * Creates a new endpoint group. A duplicate name is rejected with 409 by
     * {@link PermissionManagementService#createEndpointGroup}.
     *
     * @param request the group to create
     * @return 201 Created with no body
     */
    @PostMapping("/endpoint_group")
    public ResponseEntity<Void> createEndpointGroup(@Valid @RequestBody EndpointGroupCreateRequest request) {

        log.info("POST /endpoint_group — creating endpoint group name={}", request.getName());

        permissionManagementService.createEndpointGroup(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Partially updates an endpoint group — omitted fields are left unchanged.
     *
     * @param id the ID of the group to update
     * @param request fields to change; a non-null {@code endpointIds} replaces the group's
     *     endpoints entirely
     * @return 200 OK with the updated {@link EndpointGroupDto}
     */
    @PatchMapping("/endpoint_group/{id}")
    public ResponseEntity<EndpointGroupDto> updateEndpointGroup(
            @PathVariable Integer id, @Valid @RequestBody EndpointGroupPatchRequest request) {

        log.info("PATCH /endpoint_group/{} — updating endpoint group", id);

        EndpointGroupDto result = permissionManagementService.updateEndpointGroup(id, request);

        log.info("PATCH /endpoint_group/{} — updated successfully", id);

        return ResponseEntity.ok(result);
    }

    /**
     * Deletes an endpoint group together with every rule targeting it; its endpoints are kept.
     *
     * @param id the ID of the group to delete
     * @return 204 No Content
     * @throws de.frauas.design.backend.permission.exception.EndpointGroupNotFoundException if
     *     no endpoint group exists with the given id (404)
     */
    @DeleteMapping("/endpoint_group/{id}")
    public ResponseEntity<Void> deleteEndpointGroup(@PathVariable Integer id) {

        log.info("DELETE /endpoint_group/{} — deleting endpoint group", id);

        permissionManagementService.deleteEndpointGroup(id);

        log.info("DELETE /endpoint_group/{} — endpoint group deleted", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all permission rules of all four kinds.
     *
     * @return 200 OK with every {@link PermissionRuleDto}, in the order the permission check uses
     */
    @GetMapping("/permission_rules")
    public ResponseEntity<List<PermissionRuleDto>> getAllRules() {

        log.info("GET /permission_rules — listing all rules");

        List<PermissionRuleDto> rules = permissionManagementService.getAllRules();

        log.debug("GET /permission_rules — returning {} rules", rules.size());

        return ResponseEntity.ok(rules);
    }

    /**
     * Creates one allow/deny rule. Unknown subjects or targets are rejected with 404 and
     * duplicates with 409 by {@link PermissionManagementService#createRule}.
     *
     * @param request the rule to create
     * @return 201 Created with no body
     */
    @PostMapping("/permission_rule")
    public ResponseEntity<Void> createRule(@Valid @RequestBody PermissionRuleCreateRequest request) {

        log.info(
                "POST /permission_rule — creating rule kind={} subjectId={} targetId={} allow={}",
                request.getKind(),
                request.getSubjectId(),
                request.getTargetId(),
                request.getAllow());

        permissionManagementService.createRule(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Deletes one rule. Rule ids are only unique within a kind, so both are part of the path.
     *
     * @param kind the rule's kind, e.g. {@code USER_ENDPOINT}
     * @param id the rule's id within that kind
     * @return 204 No Content
     * @throws de.frauas.design.backend.permission.exception.PermissionRuleNotFoundException if
     *     no such rule exists (404)
     */
    @DeleteMapping("/permission_rule/{kind}/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable PermissionRuleKind kind, @PathVariable Integer id) {

        log.info("DELETE /permission_rule/{}/{} — deleting rule", kind, id);

        permissionManagementService.deleteRule(kind, id);

        log.info("DELETE /permission_rule/{}/{} — rule deleted", kind, id);

        return ResponseEntity.noContent().build();
    }
}
