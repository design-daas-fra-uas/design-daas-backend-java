package de.frauas.design.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the User REST endpoints.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UserController Integration")
class UserControllerIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_EMAIL = "ctrl.user@example.com";
    // Spring Boot 4.x removed JacksonAutoConfiguration — ObjectMapper is no longer a Spring bean.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    UserRepository userRepository;

    @BeforeAll
    void cleanup() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    // -------------------------------------------------------------------------
    // POST /user – public registration
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("POST /user – creates user with valid payload returns 201")
    void createUser_validPayload_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ctrl User",
                "email", TEST_EMAIL,
                "password", "Password1"));

        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        User created = (User) userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertFalse(created.isEnabled());
    }

    @Test
    @Order(2)
    @DisplayName("POST /user – duplicate email returns 400")
    void createUser_duplicateEmail_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ctrl User",
                "email", TEST_EMAIL,
                "password", "Password1"));

        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("POST /user – invalid email format returns 400")
    void createUser_invalidEmail_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "X",
                "email", "not-an-email",
                "password", "Password1"));

        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("POST /user – unknown user group IDs return 400")
    void createUser_unknownGroups_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Grouped User",
                "email", "unknown-group@example.com",
                "password", "Password1",
                "groups", new int[] {999999}));

        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    @DisplayName("POST /user – blank name returns 400")
    void createUser_blankName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "",
                "email", "new@example.com",
                "password", "Password1"));

        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /user/validate_email – public
    // -------------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("POST /user/validate_email – wrong code returns 400")
    void validateEmail_wrongCode_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", TEST_EMAIL, "registration_code", "WRONGCOD"));

        mockMvc.perform(post("/user/validate_email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    @DisplayName("POST /user/validate_email – blank code is rejected by bean validation")
    void validateEmail_blankCode_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", TEST_EMAIL, "registration_code", ""));

        mockMvc.perform(post("/user/validate_email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("POST /user/validate_email – correct code enables user and returns 200")
    void validateEmail_correctCode_returns200() throws Exception {
        User u = (User) userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        String code = u.getRegistrationCode();

        String body = objectMapper.writeValueAsString(Map.of(
                "email", TEST_EMAIL,
                "registration_code", code));

        mockMvc.perform(post("/user/validate_email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        User updated = (User) userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.isEnabled()).isTrue();
    }

    // -------------------------------------------------------------------------
    // GET /users – admin only
    // -------------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("GET /users – returns 200 with admin JWT")
    void getAllUsers_adminJwt_returns200() throws Exception {
        mockMvc.perform(get("/users").with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(10)
    @DisplayName("GET /users – returns 403 with user JWT")
    void getAllUsers_userJwt_returns403() throws Exception {
        mockMvc.perform(get("/users").with(userJwt(1))).andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    @DisplayName("GET /users – returns 401 with no JWT")
    void getAllUsers_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PATCH /user/{userId} – admin only
    // -------------------------------------------------------------------------

    @Test
    @Order(12)
    @DisplayName("PATCH /user/{id} – updates name")
    void updateUser_updatesName() throws Exception {
        User u = (User) userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("name", "Updated Name"));

        mockMvc.perform(patch("/user/" + u.getId())
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    // -------------------------------------------------------------------------
    // PATCH /user/{userId}/enable  &  /disable – admin only
    // -------------------------------------------------------------------------

    @Test
    @Order(13)
    @DisplayName("PATCH /user/{id} – unknown group IDs return 400")
    void updateUser_unknownGroups_returns400() throws Exception {
        User u = (User) userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("groups", new int[] {999999}));

        mockMvc.perform(patch("/user/" + u.getId())
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(14)
    @DisplayName("PATCH /user/{id}/disable then /enable toggles enabled state")
    void enableDisable_togglesState() throws Exception {
        User u = (User) userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        Integer id = u.getId();

        mockMvc.perform(patch("/user/" + id + "/disable").with(adminJwt(999)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertFalse(userRepository.findById(id).orElseThrow().isEnabled());

        mockMvc.perform(patch("/user/" + id + "/enable").with(adminJwt(999))).andExpect(status().isNoContent());
        assertTrue(userRepository.findById(id).orElseThrow().isEnabled());
    }
}
