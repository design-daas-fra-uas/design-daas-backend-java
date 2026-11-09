package de.frauas.design.backend.permission.service;

import de.frauas.design.backend.permission.dto.PermissionResultDto;
import de.frauas.design.backend.permission.model.EndpointGroupUserAccess;
import de.frauas.design.backend.permission.model.EndpointGroupUserGroupAccess;
import de.frauas.design.backend.permission.model.EndpointUserAccess;
import de.frauas.design.backend.permission.model.EndpointUserGroupAccess;
import de.frauas.design.backend.permission.repository.EndpointGroupUserAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointGroupUserGroupAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointUserAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointUserGroupAccessRepository;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves whether a given user is allowed to invoke a given design-daas function.
 *
 * <p>Resolution follows the 4-step cascade defined by the design-daas contract (ported from
 * the legacy PHP {@code PermissionCalculationService}), evaluated in order of specificity —
 * the first matching rule wins:
 * <ol>
 *   <li>direct user → endpoint rule</li>
 *   <li>user's group → endpoint rule</li>
 *   <li>user → endpoint-group rule</li>
 *   <li>user's group → endpoint-group rule</li>
 * </ol>
 *
 * <p>If multiple rows of the same specificity match inside one step (for example, duplicate
 * direct rules or conflicting group memberships), that step resolves them deterministically by
 * treating any explicit deny as stronger than allow. This preserves the 4-step precedence across
 * rule categories while remaining fail-closed for ambiguous data.</p>
 *
 * <p>Default: <b>DENY</b> — if no rule matches at any step, access is denied.
 *
 * <p>Admins skip the cascade and are always allowed: they manage the platform, and without this
 * an empty rule table would lock them out the moment design-daas switches the check on.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String UNKNOWN_USER_NAME = "unknown";
    private static final int UNRESOLVED_USER_ID = 0;

    private final EndpointUserAccessRepository endpointUserAccessRepo;
    private final EndpointUserGroupAccessRepository endpointUserGroupAccessRepo;
    private final EndpointGroupUserAccessRepository endpointGroupUserAccessRepo;
    private final EndpointGroupUserGroupAccessRepository endpointGroupUserGroupAccessRepo;
    private final UserRepository userRepository;

    /**
     * Checks whether {@code userId} is allowed to invoke {@code functionName}, applying the
     * 4-step cascade described in the class Javadoc.
     *
     * @param userId       the ID of the user to check; if unknown or {@code null}, no rule can
     *                     match and the result defaults to deny
     * @param functionName the design-daas function name being invoked; if {@code null} or
     *                     blank, the result defaults to deny without querying any rule
     * @return the permission result ({@code allow}/{@code deny}) together with basic user info
     */
    @Transactional(readOnly = true)
    public PermissionResultDto checkPermission(Integer userId, String functionName) {
        log.debug("checkPermission — userId={} functionName={}", userId, functionName);

        if (userId == null || functionName == null || functionName.isBlank()) {
            log.warn(
                    "checkPermission — invalid input (userId={}, functionName={}), defaulting to deny",
                    userId,
                    functionName);
            // PermissionResultDto backs its "user" map with Map.of(), which rejects nulls,
            // so a null userId is normalized to a placeholder id instead of being passed through.
            return PermissionResultDto.deny(userId != null ? userId : UNRESOLVED_USER_ID, UNKNOWN_USER_NAME);
        }

        Optional<BaseUser> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            log.info("checkPermission — unknown userId={}, defaulting to deny", userId);
            return PermissionResultDto.deny(userId, UNKNOWN_USER_NAME);
        }

        String userName = user.map(BaseUser::getName).orElse(UNKNOWN_USER_NAME);
        if (user.get() instanceof Admin) {
            log.info("checkPermission — userId={} is an admin, allowing fn={}", userId, functionName);
            return PermissionResultDto.allow(userId, userName);
        }
        Supplier<List<Integer>> groupIdsSupplier = new Supplier<>() {
            private List<Integer> cachedGroupIds;

            @Override
            public List<Integer> get() {
                if (cachedGroupIds == null) {
                    cachedGroupIds = userRepository.findGroupIdsByUserId(userId);
                }
                return cachedGroupIds;
            }
        };

        Optional<Boolean> allow = checkDirectUserRule(userId, functionName)
                .or(() -> checkUserGroupRule(groupIdsSupplier.get(), functionName))
                .or(() -> checkUserEndpointGroupRule(userId, functionName))
                .or(() -> checkUserGroupEndpointGroupRule(groupIdsSupplier.get(), functionName));

        if (allow.isEmpty()) {
            log.info("checkPermission — no rule matched, defaulting to deny userId={} fn={}", userId, functionName);
            return PermissionResultDto.deny(userId, userName);
        }

        log.info("checkPermission — userId={} fn={} result={}", userId, functionName, allow.get() ? "allow" : "deny");
        return toResult(allow.get(), userId, userName);
    }

    /** Step 1: direct user → endpoint rule. */
    private Optional<Boolean> checkDirectUserRule(Integer userId, String functionName) {
        return resolveStepDecision(
                endpointUserAccessRepo.findAllByUserIdAndEndpoint_FunctionNameOrderByIdAsc(userId, functionName),
                EndpointUserAccess::isAllowAccess,
                "step1 (direct user-endpoint)",
                "userId=" + userId);
    }

    /** Step 2: user's group membership → endpoint rule. */
    private Optional<Boolean> checkUserGroupRule(List<Integer> groupIds, String functionName) {
        if (groupIds.isEmpty()) {
            return Optional.empty();
        }
        return resolveStepDecision(
                endpointUserGroupAccessRepo.findAllByUserGroupIdInAndEndpoint_FunctionNameOrderByIdAsc(
                        groupIds, functionName),
                EndpointUserGroupAccess::isAllowAccess,
                "step2 (group-endpoint)",
                "groupIds=" + groupIds);
    }

    /** Step 3: user → endpoint-group rule (function must belong to that endpoint group). */
    private Optional<Boolean> checkUserEndpointGroupRule(Integer userId, String functionName) {
        return resolveStepDecision(
                endpointGroupUserAccessRepo.findAllByUserIdAndEndpointGroup_Endpoints_FunctionNameOrderByIdAsc(
                        userId, functionName),
                EndpointGroupUserAccess::isAllowAccess,
                "step3 (user-endpointGroup)",
                "userId=" + userId);
    }

    /** Step 4: user's group → endpoint-group rule (function must belong to that endpoint group). */
    private Optional<Boolean> checkUserGroupEndpointGroupRule(List<Integer> groupIds, String functionName) {
        if (groupIds.isEmpty()) {
            return Optional.empty();
        }
        return resolveStepDecision(
                endpointGroupUserGroupAccessRepo
                        .findAllByUserGroupIdInAndEndpointGroup_Endpoints_FunctionNameOrderByIdAsc(
                                groupIds, functionName),
                EndpointGroupUserGroupAccess::isAllowAccess,
                "step4 (group-endpointGroup)",
                "groupIds=" + groupIds);
    }

    private PermissionResultDto toResult(boolean allow, Integer userId, String userName) {
        return allow ? PermissionResultDto.allow(userId, userName) : PermissionResultDto.deny(userId, userName);
    }

    private <T> Optional<Boolean> resolveStepDecision(
            List<T> matches, Function<T, Boolean> allowExtractor, String stepName, String principalDescription) {
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        boolean allow = matches.stream().map(allowExtractor).allMatch(Boolean::booleanValue);
        String result = allow ? "allow" : "deny";

        if (matches.size() > 1) {
            log.warn(
                    "checkPermission — {} matched {} rules for {}, resolving result={} (explicit deny wins)",
                    stepName,
                    matches.size(),
                    principalDescription,
                    result);
        } else {
            log.debug("checkPermission — {} matched {}", stepName, principalDescription);
        }

        return Optional.of(allow);
    }
}
