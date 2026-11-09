package de.frauas.design.backend.user.service;

import de.frauas.design.backend.user.dto.UserGroupDto;
import de.frauas.design.backend.user.exception.UserGroupNameRequiredException;
import de.frauas.design.backend.user.exception.UserGroupNotFoundException;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core business logic for user-group management, including desktop-group membership
 * synchronisation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;

    /**
     * Returns every user group.
     *
     * @return all user groups
     */
    @Transactional(readOnly = true)
    public List<UserGroupDto> getAllUserGroups() {
        log.debug("getAllUserGroups");
        return userGroupRepository.findAll().stream().map(UserGroupDto::from).toList();
    }

    /**
     * Creates a new user group.
     *
     * <p>If no name is given, the description is used as the name instead — at least one of the two must be present.</p>
     *
     * @param request the group data; {@code name} falls back to {@code description} when blank
     * @return the created group
     * @throws UserGroupNameRequiredException if both {@code name} and {@code description} are blank
     */
    @Transactional
    public UserGroupDto createUserGroup(UserGroupDto request) {
        log.debug("createUserGroup — name={}", request.getName());
        UserGroup group = new UserGroup();
        group.setName(resolveGroupName(request.getName(), request.getDescription(), "createUserGroup"));
        group.setDescription(request.getDescription());
        userGroupRepository.save(group);
        log.info("createUserGroup — created id={} name={}", group.getId(), group.getName());
        return UserGroupDto.from(group);
    }

    /**
     * Updates a user group. Fields left {@code null} on {@code request} are left
     * unchanged; a non-null {@code userIds} replaces the group's membership entirely.
     *
     * @param id the ID of the group to update
     * @param request the fields to change
     * @return the updated group
     * @throws UserGroupNotFoundException if no group exists with the given id
     */
    @Transactional
    public UserGroupDto updateUserGroup(Integer id, UserGroupDto request) {
        log.debug("updateUserGroup — id={}", id);
        UserGroup group = userGroupRepository.findById(id).orElseThrow(() -> new UserGroupNotFoundException(id));
        if (request.getName() != null || request.getDescription() != null) {
            String updatedDescription =
                    request.getDescription() != null ? request.getDescription() : group.getDescription();
            String updatedName = request.getName() != null ? request.getName() : group.getName();
            group.setName(resolveGroupName(updatedName, updatedDescription, "updateUserGroup"));
            group.setDescription(updatedDescription);
        }
        if (request.getUserIds() != null) {
            syncGroupMembership(group, new HashSet<>(request.getUserIds()));
        }
        userGroupRepository.save(group);
        log.info("updateUserGroup — updated id={}", id);
        return UserGroupDto.from(group);
    }

    /**
     * Permanently deletes a user group.
     *
     * <p>The group is first removed from every member, because {@link User} owns the
     * membership join table. Its desktop-group links (owned by the group) are removed by
     * Hibernate, and its permission rules by the {@code ON DELETE CASCADE} foreign keys.
     * The members themselves are kept.</p>
     *
     * @param id the ID of the group to delete
     * @throws UserGroupNotFoundException if no group exists with the given id
     */
    @Transactional
    public void deleteUserGroup(Integer id) {
        log.debug("deleteUserGroup — id={}", id);
        UserGroup group = userGroupRepository.findById(id).orElseThrow(() -> new UserGroupNotFoundException(id));
        List<User> members = new ArrayList<>(group.getUsers());
        members.forEach(u -> u.getGroups().remove(group));
        userRepository.saveAll(members);
        group.getUsers().clear();
        userGroupRepository.delete(group);
        log.info("deleteUserGroup — deleted id={} name={} formerMembers={}", id, group.getName(), members.size());
    }

    /**
     * Replaces a group's membership to match exactly {@code desiredIds}, adding/removing
     * the group on the owning side ({@link User#getGroups()}) as needed.
     *
     * @param group the group whose membership is being synced
     * @param desiredIds the exact set of user IDs that should belong to {@code group} afterwards
     */
    private void syncGroupMembership(UserGroup group, Set<Integer> desiredIds) {
        List<User> current = new ArrayList<>(group.getUsers());
        Set<Integer> currentIds = current.stream().map(User::getId).collect(Collectors.toSet());

        // Load the full desired set (only User entities — admins have no groups)
        List<User> desired = loadDesiredUsersOrThrow(group.getId(), desiredIds);

        List<User> toRemove =
                current.stream().filter(u -> !desiredIds.contains(u.getId())).toList();
        toRemove.forEach(u -> u.getGroups().remove(group));

        List<User> toAdd =
                desired.stream().filter(u -> !currentIds.contains(u.getId())).toList();
        toAdd.forEach(u -> u.getGroups().add(group));

        List<User> changed = new ArrayList<>(toRemove.size() + toAdd.size());
        changed.addAll(toRemove);
        changed.addAll(toAdd);
        if (!changed.isEmpty()) {
            userRepository.saveAll(changed);
        }
        log.debug(
                "syncGroupMembership — groupId={} removed={} added={}",
                group.getId(),
                toRemove.stream().map(User::getId).toList(),
                toAdd.stream().map(User::getId).toList());
    }

    private String resolveGroupName(String name, String description, String operation) {
        String resolvedName = name;
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = description;
        }
        if (resolvedName == null || resolvedName.isBlank()) {
            log.warn("{} — rejected: name and description both blank", operation);
            throw new UserGroupNameRequiredException();
        }
        return resolvedName;
    }

    private List<User> loadDesiredUsersOrThrow(Integer groupId, Set<Integer> desiredIds) {
        Set<Integer> requestedIds = new LinkedHashSet<>(desiredIds);
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        List<User> desired = userRepository.findAllById(requestedIds).stream()
                .filter(BaseUser.class::isInstance)
                .filter(User.class::isInstance)
                .map(User.class::cast)
                .toList();
        Set<Integer> resolvedIds = desired.stream().map(User::getId).collect(Collectors.toSet());
        List<Integer> invalidIds =
                requestedIds.stream().filter(id -> !resolvedIds.contains(id)).toList();
        if (!invalidIds.isEmpty()) {
            log.warn("syncGroupMembership — invalid user ids={} for groupId={}", invalidIds, groupId);
            throw new IllegalArgumentException("Unknown regular user ids: " + invalidIds);
        }
        return desired;
    }
}
