package de.frauas.design.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.design.backend.permission.model.Endpoint;
import de.frauas.design.backend.permission.model.EndpointUserAccess;
import de.frauas.design.backend.permission.repository.EndpointRepository;
import de.frauas.design.backend.permission.repository.EndpointUserAccessRepository;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static de.frauas.design.backend.integration.MockJwt.userJwt;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PermissionController.
 * Covers GET /permissions/{functionName}/{userId}  and  POST /permissions_info.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PermissionController Integration")
class PermissionControllerIntegrationTest extends BaseIntegrationTest {

    // Spring Boot 4.x removed JacksonAutoConfiguration — ObjectMapper is no longer a Spring bean.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EndpointRepository endpointRepository;

    @Autowired
    EndpointUserAccessRepository endpointUserAccessRepository;

    private User testUser;

    private Endpoint testEndpoint;

    @BeforeAll
    void setup() {
        // Create test user
        userRepository.findByEmail("perm.test@example.com").ifPresent(userRepository::delete);
        User u = new User();
        u.setName("Perm Tester");
        u.setEmail("perm.test@example.com");
        u.setPassword(passwordEncoder.encode("Pass1234!"));
        u.setEnabled(true);
        testUser = userRepository.save(u);

        // Create test endpoint
        endpointRepository.findByFunctionName("test_function").ifPresent(endpointRepository::delete);
        Endpoint ep = new Endpoint();
        ep.setFunctionName("test_function");
        ep.setDescription("Test function endpoint");
        testEndpoint = endpointRepository.save(ep);
    }

    @AfterAll
    void cleanup() {
        endpointUserAccessRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(testUser.getId()))
                .forEach(endpointUserAccessRepository::delete);
        endpointRepository.delete(testEndpoint);
        userRepository.delete(testUser);
    }

    // -------------------------------------------------------------------------
    // GET /permissions/{functionName}/{userId}
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("GET /permissions – returns deny (default) when no rule exists")
    void checkPermission_noRule_returnsDeny() throws Exception {
        mockMvc.perform(get("/permissions/test_function/" + testUser.getId()).with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("deny"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /permissions – returns deny when explicit deny rule set")
    void checkPermission_denyRule_returnsDeny() throws Exception {
        EndpointUserAccess rule = new EndpointUserAccess();
        rule.setUserId(testUser.getId());
        rule.setEndpoint(testEndpoint);
        rule.setAllowAccess(false);
        endpointUserAccessRepository.save(rule);

        try {
            mockMvc.perform(get("/permissions/test_function/" + testUser.getId())
                            .with(adminJwt(999)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("deny"))
                    .andExpect(jsonPath("$.user.id").value(testUser.getId()));
        } finally {
            endpointUserAccessRepository.delete(rule);
        }
    }

    @Test
    @Order(3)
    @DisplayName("GET /permissions – returns allow when explicit allow rule set")
    void checkPermission_allowRule_returnsAllow() throws Exception {
        EndpointUserAccess rule = new EndpointUserAccess();
        rule.setUserId(testUser.getId());
        rule.setEndpoint(testEndpoint);
        rule.setAllowAccess(true);
        endpointUserAccessRepository.save(rule);

        try {
            mockMvc.perform(get("/permissions/test_function/" + testUser.getId())
                            .with(adminJwt(999)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("allow"));
        } finally {
            endpointUserAccessRepository.delete(rule);
        }
    }

    @Test
    @Order(4)
    @DisplayName("GET /permissions – a duplicate direct rule is refused, the existing rule still decides")
    void checkPermission_duplicateDirectRule_isRefusedByDatabase() throws Exception {
        EndpointUserAccess denyRule = new EndpointUserAccess();
        denyRule.setUserId(testUser.getId());
        denyRule.setEndpoint(testEndpoint);
        denyRule.setAllowAccess(false);
        endpointUserAccessRepository.save(denyRule);

        EndpointUserAccess allowRule = new EndpointUserAccess();
        allowRule.setUserId(testUser.getId());
        allowRule.setEndpoint(testEndpoint);
        allowRule.setAllowAccess(true);

        try {
            assertThrows(DataIntegrityViolationException.class, () -> endpointUserAccessRepository.save(allowRule));
            mockMvc.perform(get("/permissions/test_function/" + testUser.getId())
                            .with(adminJwt(999)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("deny"));
        } finally {
            endpointUserAccessRepository.delete(denyRule);
        }
    }

    @Test
    @Order(5)
    @DisplayName("GET /permissions – returns 403 with user JWT (not admin)")
    void checkPermission_userJwt_returns403() throws Exception {
        mockMvc.perform(get("/permissions/test_function/" + testUser.getId()).with(userJwt(testUser.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("GET /permissions – returns 401 without authentication")
    void checkPermission_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/permissions/test_function/" + testUser.getId())).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // POST /permissions_info
    // -------------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("POST /permissions_info – returns deny for authenticated user (default, no rule)")
    void permissionsInfo_authenticated_returnsDeny() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("function_name", "test_function"));

        mockMvc.perform(post("/permissions_info")
                        .with(userJwt(testUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("deny"));
    }

    @Test
    @Order(8)
    @DisplayName("POST /permissions_info – returns deny when deny rule set for the JWT user")
    void permissionsInfo_denyRule_returnsDeny() throws Exception {
        EndpointUserAccess rule = new EndpointUserAccess();
        rule.setUserId(testUser.getId());
        rule.setEndpoint(testEndpoint);
        rule.setAllowAccess(false);
        endpointUserAccessRepository.save(rule);

        try {
            String body = objectMapper.writeValueAsString(Map.of("function_name", "test_function"));

            mockMvc.perform(post("/permissions_info")
                            .with(userJwt(testUser.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("deny"));
        } finally {
            endpointUserAccessRepository.delete(rule);
        }
    }

    @Test
    @Order(9)
    @DisplayName("POST /permissions_info – returns allow when allow rule set for the JWT user")
    void permissionsInfo_allowRule_returnsAllow() throws Exception {
        EndpointUserAccess rule = new EndpointUserAccess();
        rule.setUserId(testUser.getId());
        rule.setEndpoint(testEndpoint);
        rule.setAllowAccess(true);
        endpointUserAccessRepository.save(rule);

        try {
            String body = objectMapper.writeValueAsString(Map.of("function_name", "test_function"));

            mockMvc.perform(post("/permissions_info")
                            .with(userJwt(testUser.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("allow"));
        } finally {
            endpointUserAccessRepository.delete(rule);
        }
    }

    @Test
    @Order(10)
    @DisplayName("POST /permissions_info – returns 400 when function_name is blank")
    void permissionsInfo_blankFunctionName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("function_name", "   "));

        mockMvc.perform(post("/permissions_info")
                        .with(userJwt(testUser.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    @DisplayName("POST /permissions_info – returns 401 without JWT")
    void permissionsInfo_noAuth_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("function_name", "test_function"));

        mockMvc.perform(post("/permissions_info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    @DisplayName("GET /permissions – admins are allowed without any rule")
    void checkPermission_admin_returnsAllow() throws Exception {
        userRepository.findByEmail("perm.admin@example.com").ifPresent(userRepository::delete);
        Admin admin = new Admin();
        admin.setName("Perm Admin");
        admin.setEmail("perm.admin@example.com");
        admin.setPassword(passwordEncoder.encode("Pass1234!"));
        admin.setEnabled(true);
        admin = userRepository.save(admin);

        try {
            mockMvc.perform(get("/permissions/test_function/" + admin.getId()).with(adminJwt(999)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("allow"));
        } finally {
            userRepository.delete(admin);
        }
    }
}
