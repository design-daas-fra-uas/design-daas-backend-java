package de.frauas.design.backend.permission.service;

import de.frauas.design.backend.permission.dto.EndpointCreateRequest;
import de.frauas.design.backend.permission.dto.EndpointDto;
import de.frauas.design.backend.permission.dto.EndpointGroupCreateRequest;
import de.frauas.design.backend.permission.dto.EndpointGroupDto;
import de.frauas.design.backend.permission.dto.EndpointGroupPatchRequest;
import de.frauas.design.backend.permission.dto.PermissionRuleCreateRequest;
import de.frauas.design.backend.permission.dto.PermissionRuleDto;
import de.frauas.design.backend.permission.dto.PermissionRuleKind;
import de.frauas.design.backend.permission.exception.EndpointAlreadyExistsException;
import de.frauas.design.backend.permission.exception.EndpointGroupAlreadyExistsException;
import de.frauas.design.backend.permission.exception.EndpointGroupNotFoundException;
import de.frauas.design.backend.permission.exception.EndpointNotFoundException;
import de.frauas.design.backend.permission.exception.PermissionRuleAlreadyExistsException;
import de.frauas.design.backend.permission.exception.PermissionRuleNotFoundException;
import de.frauas.design.backend.permission.model.Endpoint;
import de.frauas.design.backend.permission.model.EndpointGroup;
import de.frauas.design.backend.permission.model.EndpointGroupUserAccess;
import de.frauas.design.backend.permission.model.EndpointGroupUserGroupAccess;
import de.frauas.design.backend.permission.model.EndpointUserAccess;
import de.frauas.design.backend.permission.model.EndpointUserGroupAccess;
import de.frauas.design.backend.permission.repository.EndpointGroupRepository;
import de.frauas.design.backend.permission.repository.EndpointGroupUserAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointGroupUserGroupAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointRepository;
import de.frauas.design.backend.permission.repository.EndpointUserAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointUserGroupAccessRepository;
import de.frauas.design.backend.user.exception.AccountNotFoundException;
import de.frauas.design.backend.user.exception.UserGroupNotFoundException;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core business logic for managing the permission data that {@link PermissionService}
 * evaluates: endpoints (design-daas functions), endpoint groups, and the four kinds of
 * allow/deny rule (see {@link PermissionRuleKind}).
 *
 * <p>Deleting an endpoint or endpoint group first deletes the rules and group memberships
 * that point to it, so the result is the same on MariaDB (which would cascade anyway) and on
 * the H2 test database (which has no cascading foreign keys).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionManagementService {

    private final EndpointRepository endpointRepository;
    private final EndpointGroupRepository endpointGroupRepository;
    private final EndpointUserAccessRepository endpointUserAccessRepo;
    private final EndpointUserGroupAccessRepository endpointUserGroupAccessRepo;
    private final EndpointGroupUserAccessRepository endpointGroupUserAccessRepo;
    private final EndpointGroupUserGroupAccessRepository endpointGroupUserGroupAccessRepo;
    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    /**
     * Returns every endpoint.
     *
     * @return all endpoints
     */
    @Transactional(readOnly = true)
    public List<EndpointDto> getAllEndpoints() {
        log.debug("getAllEndpoints");
        return endpointRepository.findAll().stream().map(EndpointDto::from).toList();
    }

    /**
     * Registers a design-daas function so rules can target it.
     *
     * @param request the function name and optional description
     * @throws EndpointAlreadyExistsException if the function name is already registered
     */
    @Transactional
    public void createEndpoint(EndpointCreateRequest request) {
        log.debug("createEndpoint — functionName={}", request.getFunctionName());
        if (endpointRepository.existsByFunctionName(request.getFunctionName())) {
            log.warn("createEndpoint — functionName={} already exists", request.getFunctionName());
            throw new EndpointAlreadyExistsException(request.getFunctionName());
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setFunctionName(request.getFunctionName());
        endpoint.setDescription(request.getDescription());
        endpointRepository.save(endpoint);
        log.info("createEndpoint — created id={} functionName={}", endpoint.getId(), endpoint.getFunctionName());
    }

    /**
     * Deletes an endpoint together with every rule targeting it, and removes it from all
     * endpoint groups.
     *
     * @param id the ID of the endpoint to delete
     * @throws EndpointNotFoundException if no endpoint exists with the given id
     */
    @Transactional
    public void deleteEndpoint(Integer id) {
        log.debug("deleteEndpoint — id={}", id);
        Endpoint endpoint = findEndpointById(id);
        endpointUserAccessRepo.deleteAllByEndpoint_Id(id);
        endpointUserGroupAccessRepo.deleteAllByEndpoint_Id(id);
        List<EndpointGroup> groups = endpointGroupRepository.findAllByEndpoints_Id(id);
        groups.forEach(g -> g.getEndpoints().remove(endpoint));
        endpointGroupRepository.saveAll(groups);
        endpointRepository.delete(endpoint);
        log.info("deleteEndpoint — deleted id={} functionName={}", id, endpoint.getFunctionName());
    }

    // -------------------------------------------------------------------------
    // Endpoint groups
    // -------------------------------------------------------------------------

    /**
     * Returns every endpoint group with its endpoint ids.
     *
     * @return all endpoint groups
     */
    @Transactional(readOnly = true)
    public List<EndpointGroupDto> getAllEndpointGroups() {
        log.debug("getAllEndpointGroups");
        return endpointGroupRepository.findAll().stream().map(EndpointGroupDto::from).toList();
    }

    /**
     * Creates an endpoint group.
     *
     * @param request the group's name, description and endpoint ids
     * @throws EndpointGroupAlreadyExistsException if the name is already taken
     * @throws IllegalArgumentException if an endpoint id is unknown
     */
    @Transactional
    public void createEndpointGroup(EndpointGroupCreateRequest request) {
        log.debug("createEndpointGroup — name={}", request.getName());
        if (endpointGroupRepository.existsByName(request.getName())) {
            log.warn("createEndpointGroup — name={} already exists", request.getName());
            throw new EndpointGroupAlreadyExistsException(request.getName());
        }
        EndpointGroup group = new EndpointGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        if (request.getEndpointIds() != null) {
            group.setEndpoints(loadEndpointsOrThrow(request.getEndpointIds()));
        }
        endpointGroupRepository.save(group);
        log.info("createEndpointGroup — created id={} name={}", group.getId(), group.getName());
    }

    /**
     * Partially updates an endpoint group. Fields left {@code null} on {@code request} are
     * left unchanged; a non-null {@code endpointIds} replaces the group's endpoints entirely.
     *
     * @param id the ID of the group to update
     * @param request the fields to change
     * @return the updated group
     * @throws EndpointGroupNotFoundException if no group exists with the given id
     * @throws EndpointGroupAlreadyExistsException if the new name is taken by another group
     * @throws IllegalArgumentException if an endpoint id is unknown
     */
    @Transactional
    public EndpointGroupDto updateEndpointGroup(Integer id, EndpointGroupPatchRequest request) {
        log.debug("updateEndpointGroup — id={}", id);
        EndpointGroup group = findEndpointGroupById(id);
        if (request.getName() != null && !request.getName().equals(group.getName())) {
            if (endpointGroupRepository.existsByName(request.getName())) {
                log.warn("updateEndpointGroup — name={} already exists", request.getName());
                throw new EndpointGroupAlreadyExistsException(request.getName());
            }
            group.setName(request.getName());
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }
        if (request.getEndpointIds() != null) {
            group.setEndpoints(loadEndpointsOrThrow(request.getEndpointIds()));
        }
        endpointGroupRepository.save(group);
        log.info("updateEndpointGroup — updated id={}", id);
        return EndpointGroupDto.from(group);
    }

    /**
     * Deletes an endpoint group together with every rule targeting it. The endpoints in
     * it are kept.
     *
     * @param id the ID of the group to delete
     * @throws EndpointGroupNotFoundException if no group exists with the given id
     */
    @Transactional
    public void deleteEndpointGroup(Integer id) {
        log.debug("deleteEndpointGroup — id={}", id);
        EndpointGroup group = findEndpointGroupById(id);
        endpointGroupUserAccessRepo.deleteAllByEndpointGroup_Id(id);
        endpointGroupUserGroupAccessRepo.deleteAllByEndpointGroup_Id(id);
        endpointGroupRepository.delete(group);
        log.info("deleteEndpointGroup — deleted id={} name={}", id, group.getName());
    }

    // -------------------------------------------------------------------------
    // Rules
    // -------------------------------------------------------------------------

    /**
     * Returns every rule of all four kinds, in the order {@link PermissionService} checks them.
     *
     * @return all permission rules
     */
    @Transactional(readOnly = true)
    public List<PermissionRuleDto> getAllRules() {
        log.debug("getAllRules");
        List<PermissionRuleDto> rules = new ArrayList<>();
        endpointUserAccessRepo.findAll().forEach(r -> rules.add(toDto(
                PermissionRuleKind.USER_ENDPOINT, r.getId(), r.getUserId(), r.getEndpoint(), r.isAllowAccess())));
        endpointUserGroupAccessRepo.findAll().forEach(r -> rules.add(toDto(
                PermissionRuleKind.USER_GROUP_ENDPOINT,
                r.getId(),
                r.getUserGroupId(),
                r.getEndpoint(),
                r.isAllowAccess())));
        endpointGroupUserAccessRepo.findAll().forEach(r -> rules.add(toDto(
                PermissionRuleKind.USER_ENDPOINT_GROUP,
                r.getId(),
                r.getUserId(),
                r.getEndpointGroup(),
                r.isAllowAccess())));
        endpointGroupUserGroupAccessRepo.findAll().forEach(r -> rules.add(toDto(
                PermissionRuleKind.USER_GROUP_ENDPOINT_GROUP,
                r.getId(),
                r.getUserGroupId(),
                r.getEndpointGroup(),
                r.isAllowAccess())));
        return rules;
    }

    /**
     * Creates one allow/deny rule.
     *
     * @param request the rule's kind, subject, target and decision
     * @throws AccountNotFoundException if a user-subject rule names an unknown account
     * @throws UserGroupNotFoundException if a group-subject rule names an unknown user group
     * @throws EndpointNotFoundException if the target endpoint does not exist
     * @throws EndpointGroupNotFoundException if the target endpoint group does not exist
     * @throws PermissionRuleAlreadyExistsException if the same rule already exists
     */
    @Transactional
    public void createRule(PermissionRuleCreateRequest request) {
        PermissionRuleKind kind = request.getKind();
        Integer subjectId = request.getSubjectId();
        Integer targetId = request.getTargetId();
        boolean allow = request.getAllow();
        log.debug("createRule — kind={} subjectId={} targetId={} allow={}", kind, subjectId, targetId, allow);
        assertSubjectExists(kind, subjectId);
        Integer ruleId = switch (kind) {
            case USER_ENDPOINT -> createUserEndpointRule(subjectId, targetId, allow);
            case USER_GROUP_ENDPOINT -> createUserGroupEndpointRule(subjectId, targetId, allow);
            case USER_ENDPOINT_GROUP -> createUserEndpointGroupRule(subjectId, targetId, allow);
            case USER_GROUP_ENDPOINT_GROUP -> createUserGroupEndpointGroupRule(subjectId, targetId, allow);
        };
        log.info("createRule — created kind={} id={} subjectId={} targetId={}", kind, ruleId, subjectId, targetId);
    }

    /**
     * Deletes one rule. Rule ids are only unique within a kind, so both are needed.
     *
     * @param kind the rule's kind
     * @param id the rule's id within that kind
     * @throws PermissionRuleNotFoundException if no such rule exists
     */
    @Transactional
    public void deleteRule(PermissionRuleKind kind, Integer id) {
        log.debug("deleteRule — kind={} id={}", kind, id);
        boolean exists = switch (kind) {
            case USER_ENDPOINT -> endpointUserAccessRepo.existsById(id);
            case USER_GROUP_ENDPOINT -> endpointUserGroupAccessRepo.existsById(id);
            case USER_ENDPOINT_GROUP -> endpointGroupUserAccessRepo.existsById(id);
            case USER_GROUP_ENDPOINT_GROUP -> endpointGroupUserGroupAccessRepo.existsById(id);
        };
        if (!exists) {
            log.warn("deleteRule — kind={} id={} not found", kind, id);
            throw new PermissionRuleNotFoundException(kind, id);
        }
        switch (kind) {
            case USER_ENDPOINT -> endpointUserAccessRepo.deleteById(id);
            case USER_GROUP_ENDPOINT -> endpointUserGroupAccessRepo.deleteById(id);
            case USER_ENDPOINT_GROUP -> endpointGroupUserAccessRepo.deleteById(id);
            case USER_GROUP_ENDPOINT_GROUP -> endpointGroupUserGroupAccessRepo.deleteById(id);
        }
        log.info("deleteRule — deleted kind={} id={}", kind, id);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Integer createUserEndpointRule(Integer userId, Integer endpointId, boolean allow) {
        Endpoint endpoint = findEndpointById(endpointId);
        if (endpointUserAccessRepo.existsByUserIdAndEndpoint_Id(userId, endpointId)) {
            throw duplicateRule(PermissionRuleKind.USER_ENDPOINT, userId, endpointId);
        }
        EndpointUserAccess rule = new EndpointUserAccess();
        rule.setUserId(userId);
        rule.setEndpoint(endpoint);
        rule.setAllowAccess(allow);
        return endpointUserAccessRepo.save(rule).getId();
    }

    private Integer createUserGroupEndpointRule(Integer userGroupId, Integer endpointId, boolean allow) {
        Endpoint endpoint = findEndpointById(endpointId);
        if (endpointUserGroupAccessRepo.existsByUserGroupIdAndEndpoint_Id(userGroupId, endpointId)) {
            throw duplicateRule(PermissionRuleKind.USER_GROUP_ENDPOINT, userGroupId, endpointId);
        }
        EndpointUserGroupAccess rule = new EndpointUserGroupAccess();
        rule.setUserGroupId(userGroupId);
        rule.setEndpoint(endpoint);
        rule.setAllowAccess(allow);
        return endpointUserGroupAccessRepo.save(rule).getId();
    }

    private Integer createUserEndpointGroupRule(Integer userId, Integer endpointGroupId, boolean allow) {
        EndpointGroup group = findEndpointGroupById(endpointGroupId);
        if (endpointGroupUserAccessRepo.existsByUserIdAndEndpointGroup_Id(userId, endpointGroupId)) {
            throw duplicateRule(PermissionRuleKind.USER_ENDPOINT_GROUP, userId, endpointGroupId);
        }
        EndpointGroupUserAccess rule = new EndpointGroupUserAccess();
        rule.setUserId(userId);
        rule.setEndpointGroup(group);
        rule.setAllowAccess(allow);
        return endpointGroupUserAccessRepo.save(rule).getId();
    }

    private Integer createUserGroupEndpointGroupRule(Integer userGroupId, Integer endpointGroupId, boolean allow) {
        EndpointGroup group = findEndpointGroupById(endpointGroupId);
        if (endpointGroupUserGroupAccessRepo.existsByUserGroupIdAndEndpointGroup_Id(userGroupId, endpointGroupId)) {
            throw duplicateRule(PermissionRuleKind.USER_GROUP_ENDPOINT_GROUP, userGroupId, endpointGroupId);
        }
        EndpointGroupUserGroupAccess rule = new EndpointGroupUserGroupAccess();
        rule.setUserGroupId(userGroupId);
        rule.setEndpointGroup(group);
        rule.setAllowAccess(allow);
        return endpointGroupUserGroupAccessRepo.save(rule).getId();
    }

    private PermissionRuleAlreadyExistsException duplicateRule(
            PermissionRuleKind kind, Integer subjectId, Integer targetId) {
        log.warn("createRule — kind={} subjectId={} targetId={} already exists", kind, subjectId, targetId);
        return new PermissionRuleAlreadyExistsException(kind, subjectId, targetId);
    }

    /**
     * Checks that a rule's subject exists. Rules for a single user may target any account,
     * admins included, because design-daas checks admins too.
     *
     * @param kind the rule's kind, which decides whether the subject is a user or a user group
     * @param subjectId the user or user-group ID
     * @throws AccountNotFoundException if no account exists with the given id
     * @throws UserGroupNotFoundException if no user group exists with the given id
     */
    private void assertSubjectExists(PermissionRuleKind kind, Integer subjectId) {
        if (kind.hasGroupSubject()) {
            if (!userGroupRepository.existsById(subjectId)) {
                log.warn("createRule — userGroupId={} not found", subjectId);
                throw new UserGroupNotFoundException(subjectId);
            }
        } else if (!userRepository.existsById(subjectId)) {
            log.warn("createRule — userId={} not found", subjectId);
            throw new AccountNotFoundException(subjectId);
        }
    }

    private Endpoint findEndpointById(Integer id) {
        return endpointRepository.findById(id).orElseThrow(() -> {
            log.warn("findEndpointById — endpointId={} not found", id);
            return new EndpointNotFoundException(id);
        });
    }

    private EndpointGroup findEndpointGroupById(Integer id) {
        return endpointGroupRepository.findById(id).orElseThrow(() -> {
            log.warn("findEndpointGroupById — endpointGroupId={} not found", id);
            return new EndpointGroupNotFoundException(id);
        });
    }

    private List<Endpoint> loadEndpointsOrThrow(List<Integer> endpointIds) {
        Set<Integer> requestedIds = new LinkedHashSet<>(endpointIds);
        if (requestedIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Endpoint> endpoints = endpointRepository.findAllById(requestedIds);
        Set<Integer> foundIds = endpoints.stream().map(Endpoint::getId).collect(Collectors.toSet());
        List<Integer> missingIds =
                requestedIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            log.warn("loadEndpointsOrThrow — unknown endpoint ids={}", missingIds);
            throw new IllegalArgumentException("Unknown endpoint ids: " + missingIds);
        }
        return new ArrayList<>(endpoints);
    }

    private PermissionRuleDto toDto(
            PermissionRuleKind kind, Integer id, Integer subjectId, Endpoint target, boolean allow) {
        return PermissionRuleDto.builder()
                .id(id)
                .kind(kind)
                .subjectId(subjectId)
                .targetId(target.getId())
                .targetName(target.getFunctionName())
                .allow(allow)
                .build();
    }

    private PermissionRuleDto toDto(
            PermissionRuleKind kind, Integer id, Integer subjectId, EndpointGroup target, boolean allow) {
        return PermissionRuleDto.builder()
                .id(id)
                .kind(kind)
                .subjectId(subjectId)
                .targetId(target.getId())
                .targetName(target.getName())
                .allow(allow)
                .build();
    }
}
