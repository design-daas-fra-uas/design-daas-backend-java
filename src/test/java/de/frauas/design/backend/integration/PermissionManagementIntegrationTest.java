package de.frauas.design.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.design.backend.permission.model.EndpointGroup;
import de.frauas.design.backend.permission.repository.EndpointGroupRepository;
import de.frauas.design.backend.permission.repository.EndpointRepository;
import de.frauas.design.backend.permission.repository.EndpointUserAccessRepository;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static de.frauas.design.backend.integration.MockJwt.userJwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the permission management API ({@code /endpoint(s)},
 * {@code /endpoint_group(s)}, {@code /permission_rule(s)}), checked end-to-end against
 * {@code GET /permissions/{functionName}/{userId}}. All endpoints require the
 * {@code SCOPE_admin} authority.
 */
@DisplayName("PermissionManagementController Integration")
class PermissionManagementIntegrationTest extends BaseIntegrationTest {

    private static final String FUNCTION = "mgmt_test_function";
    private static final String ENDPOINT_GROUP = "mgmt-test-endpoint-group";
    private static final String USER_EMAIL = "mgmt.user@example.com";
    private static final String USER_GROUP = "mgmt-test-user-group";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    EndpointRepository endpointRepository;

    @Autowired
    EndpointGroupRepository endpointGroupRepository;

    @Autowired
    EndpointUserAccessRepository endpointUserAccessRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserGroupRepository userGroupRepository;

    @BeforeEach
    void cleanup() {
        // Deleting through the API also removes the rules that point to these rows.
        findEndpointGroupId().ifPresent(id -> performAsAdmin(delete("/endpoint_group/" + id)));
        endpointRepository
                .findByFunctionName(FUNCTION)
                .ifPresent(e -> performAsAdmin(delete("/endpoint/" + e.getId())));
        userRepository.findByEmail(USER_EMAIL).ifPresent(userRepository::delete);
        userGroupRepository.findByName(USER_GROUP).ifPresent(userGroupRepository::delete);
    }

    private void performAsAdmin(MockHttpServletRequestBuilder request) {
        try {
            mockMvc.perform(request.with(adminJwt(999)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .with(adminJwt(999))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Integer createEndpoint() throws Exception {
        postJson("/endpoint", Map.of("functionName", FUNCTION)).andExpect(status().isCreated());
        return endpointRepository.findByFunctionName(FUNCTION).orElseThrow().getId();
    }

    private Optional<Integer> findEndpointGroupId() {
        return endpointGroupRepository.findAll().stream()
                .filter(g -> g.getName().equals(ENDPOINT_GROUP))
                .map(EndpointGroup::getId)
                .findFirst();
    }

    private User saveUser(UserGroup group) {
        User user = new User();
        user.setName("Mgmt User");
        user.setEmail(USER_EMAIL);
        user.setPassword("encoded");
        user.setEnabled(true);
        if (group != null) {
            user.getGroups().add(group);
        }
        return userRepository.save(user);
    }

    private void expectPermission(Integer userId, String result) throws Exception {
        mockMvc.perform(get("/permissions/" + FUNCTION + "/" + userId).with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(result));
    }

    @Test
    @DisplayName("GET /endpoints – user JWT returns 403, no JWT returns 401")
    void nonAdmin_isRejected() throws Exception {
        mockMvc.perform(get("/endpoints").with(userJwt(1))).andExpect(status().isForbidden());
        mockMvc.perform(get("/permission_rules")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST/DELETE /endpoint – create, duplicate 409, invalid name 400, list, delete")
    void endpointLifecycle() throws Exception {
        postJson("/endpoint", Map.of("functionName", FUNCTION, "description", "test"))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
        Integer id = endpointRepository.findByFunctionName(FUNCTION).orElseThrow().getId();

        postJson("/endpoint", Map.of("functionName", FUNCTION)).andExpect(status().isConflict());
        postJson("/endpoint", Map.of("functionName", "bad name!")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/endpoints").with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.functionName == '" + FUNCTION + "')]").exists());

        mockMvc.perform(delete("/endpoint/" + id).with(adminJwt(999))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/endpoint/" + id).with(adminJwt(999))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /endpoint_group – blank name returns 400")
    void createEndpointGroup_blankName_returns400() throws Exception {
        postJson("/endpoint_group", Map.of("name", "")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("USER_ENDPOINT rule – allow, duplicate 409, deleting the rule falls back to deny")
    void userEndpointRule_drivesPermissionCheck() throws Exception {
        User user = saveUser(null);
        Integer endpointId = createEndpoint();
        expectPermission(user.getId(), "deny");

        Map<String, Object> rule =
                Map.of("kind", "USER_ENDPOINT", "subjectId", user.getId(), "targetId", endpointId, "allow", true);
        postJson("/permission_rule", rule).andExpect(status().isCreated());
        expectPermission(user.getId(), "allow");

        mockMvc.perform(get("/permission_rules").with(adminJwt(999)))
                .andExpect(jsonPath("$[?(@.targetName == '" + FUNCTION + "')].kind").value("USER_ENDPOINT"));
        postJson("/permission_rule", rule).andExpect(status().isConflict());

        Integer ruleId = endpointUserAccessRepository
                .findAllByUserIdAndEndpoint_FunctionNameOrderByIdAsc(user.getId(), FUNCTION)
                .getFirst()
                .getId();
        mockMvc.perform(delete("/permission_rule/USER_ENDPOINT/" + ruleId).with(adminJwt(999)))
                .andExpect(status().isNoContent());
        expectPermission(user.getId(), "deny");
    }

    @Test
    @DisplayName("USER_GROUP_ENDPOINT_GROUP rule grants every endpoint in the group")
    void groupRule_viaEndpointGroup() throws Exception {
        UserGroup userGroup = new UserGroup();
        userGroup.setName(USER_GROUP);
        userGroup = userGroupRepository.save(userGroup);
        User user = saveUser(userGroup);
        Integer endpointId = createEndpoint();

        postJson("/endpoint_group", Map.of("name", ENDPOINT_GROUP, "endpointIds", List.of(endpointId)))
                .andExpect(status().isCreated());
        Integer endpointGroupId = findEndpointGroupId().orElseThrow();
        mockMvc.perform(get("/endpoint_groups").with(adminJwt(999)))
                .andExpect(jsonPath("$[?(@.name == '" + ENDPOINT_GROUP + "')].endpointIds[0]")
                        .value(endpointId));

        postJson(
                        "/permission_rule",
                        Map.of(
                                "kind", "USER_GROUP_ENDPOINT_GROUP",
                                "subjectId", userGroup.getId(),
                                "targetId", endpointGroupId,
                                "allow", true))
                .andExpect(status().isCreated());
        expectPermission(user.getId(), "allow");

        // Deleting the endpoint group also deletes its rule.
        mockMvc.perform(delete("/endpoint_group/" + endpointGroupId).with(adminJwt(999)))
                .andExpect(status().isNoContent());
        expectPermission(user.getId(), "deny");
    }

    @Test
    @DisplayName("POST /permission_rule – unknown subject or target returns 404, unknown kind returns 400")
    void rule_invalidReferences() throws Exception {
        Integer endpointId = createEndpoint();

        postJson(
                        "/permission_rule",
                        Map.of("kind", "USER_ENDPOINT", "subjectId", 987654, "targetId", endpointId, "allow", true))
                .andExpect(status().isNotFound());
        User user = saveUser(null);
        postJson(
                        "/permission_rule",
                        Map.of("kind", "USER_ENDPOINT", "subjectId", user.getId(), "targetId", 987654, "allow", true))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/permission_rule/NOT_A_KIND/1").with(adminJwt(999)))
                .andExpect(status().isBadRequest());
    }
}
