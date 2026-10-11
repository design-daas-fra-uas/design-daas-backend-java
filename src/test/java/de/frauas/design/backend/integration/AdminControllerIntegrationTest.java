package de.frauas.design.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Admin REST endpoints ({@code /admins}, {@code /admin/**}).
 * All endpoints require the {@code SCOPE_admin} authority.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AdminController Integration")
class AdminControllerIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_EMAIL = "ctrl.admin@example.com";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    UserRepository userRepository;

    @BeforeAll
    void cleanup() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @Order(1)
    @DisplayName("POST /admin – no JWT returns 401")
    void createAdmin_noJwt_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ctrl Admin",
                "email", TEST_EMAIL,
                "password", "Password1"));

        mockMvc.perform(post("/admin").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("POST /admin – user JWT returns 403")
    void createAdmin_userJwt_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ctrl Admin",
                "email", TEST_EMAIL,
                "password", "Password1"));

        mockMvc.perform(post("/admin")
                        .with(userJwt(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("POST /admin – admin JWT creates enabled admin, returns 201")
    void createAdmin_adminJwt_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Ctrl Admin",
                "email", TEST_EMAIL,
                "password", "Password1"));

        mockMvc.perform(post("/admin")
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
    }

    @Test
    @Order(4)
    @DisplayName("GET /admins – returns list including created admin")
    void getAllAdmins_returns200() throws Exception {
        mockMvc.perform(get("/admins").with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.email=='" + TEST_EMAIL + "')]").exists());
    }
}
