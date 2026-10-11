package de.frauas.design.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static de.frauas.design.backend.integration.MockJwt.userJwt;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the UserGroup REST endpoints ({@code /user_group(s)}).
 * All endpoints require the {@code SCOPE_admin} authority.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UserGroupController Integration")
class UserGroupControllerIntegrationTest extends BaseIntegrationTest {

    private static final String GROUP_NAME = "ctrl-test-group";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    UserGroupRepository userGroupRepository;

    @BeforeAll
    void cleanup() {
        userGroupRepository.findByName(GROUP_NAME).ifPresent(userGroupRepository::delete);
    }

    @Test
    @Order(1)
    @DisplayName("POST /user_group – no JWT returns 401")
    void createUserGroup_noJwt_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", GROUP_NAME));

        mockMvc.perform(post("/user_group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("POST /user_group – user JWT returns 403")
    void createUserGroup_userJwt_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", GROUP_NAME));

        mockMvc.perform(post("/user_group")
                        .with(userJwt(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("POST /user_group – admin JWT creates group, returns 201")
    void createUserGroup_adminJwt_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", GROUP_NAME, "description", "Created by integration test"));

        mockMvc.perform(post("/user_group")
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
    }

    @Test
    @Order(4)
    @DisplayName("POST /user_group – blank name falls back to description")
    void createUserGroup_blankName_fallsBackToDescription() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "",
                "description", "fallback-desc-group"));

        mockMvc.perform(post("/user_group")
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertTrue(userGroupRepository.findByName("fallback-desc-group").isPresent());
        userGroupRepository.findByName("fallback-desc-group").ifPresent(userGroupRepository::delete);
    }

    @Test
    @Order(5)
    @DisplayName("POST /user_group – blank name and description returns 400")
    void createUserGroup_blankNameAndDescription_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "", "description", ""));

        mockMvc.perform(post("/user_group")
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("GET /user_groups – returns list including created group")
    void getAllUserGroups_returns200() throws Exception {
        mockMvc.perform(get("/user_groups").with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name=='" + GROUP_NAME + "')]").exists());
    }

    @Test
    @Order(7)
    @DisplayName("PATCH /user_group/{id} – updates description")
    void updateUserGroup_patch_updatesDescription() throws Exception {
        UserGroup group = userGroupRepository.findByName(GROUP_NAME).orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("description", "Updated via PATCH"));

        mockMvc.perform(patch("/user_group/" + group.getId())
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated via PATCH"));
    }

    @Test
    @Order(8)
    @DisplayName("PATCH /user_group/{id} – invalid member IDs return 400")
    void updateUserGroup_invalidMemberIds_returns400() throws Exception {
        UserGroup group = userGroupRepository.findByName(GROUP_NAME).orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("userIds", new int[] {999999}));

        mockMvc.perform(patch("/user_group/" + group.getId())
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("PATCH /user_group/{id} – blank name and description return 400")
    void updateUserGroup_blankNameAndDescription_returns400() throws Exception {
        UserGroup group = userGroupRepository.findByName(GROUP_NAME).orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("name", "", "description", ""));

        mockMvc.perform(patch("/user_group/" + group.getId())
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
