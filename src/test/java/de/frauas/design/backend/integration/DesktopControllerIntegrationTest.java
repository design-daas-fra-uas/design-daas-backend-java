package de.frauas.design.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.util.Map;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static de.frauas.design.backend.integration.MockJwt.userJwt;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code DesktopController}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Desktop Controller Integration")
class DesktopControllerIntegrationTest extends BaseIntegrationTest {

    // Spring Boot 4.x removed JacksonAutoConfiguration — ObjectMapper is no longer a Spring bean.
    // Construct directly for request-body serialization.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Integer createdDesktopId;

    // -------------------------------------------------------------------------
    // POST /desktop – admin only
    // -------------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("POST /desktop – creates desktop with admin JWT")
    void createDesktop_adminJwt_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "description", "Integration Test Desktop"));

        mockMvc.perform(post("/desktop")
                        .with(adminJwt(999))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        String response = mockMvc.perform(get("/desktops").with(adminJwt(999)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (var desktop : objectMapper.readTree(response)) {
            if ("Integration Test Desktop".equals(desktop.get("description").asText())) {
                createdDesktopId = desktop.get("id").asInt();
            }
        }
        assertNotNull(createdDesktopId);
    }

    @Test
    @Order(3)
    @DisplayName("POST /desktop – returns 403 with user JWT")
    void createDesktop_userJwt_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "description", "Should not be created"));

        mockMvc.perform(post("/desktop")
                        .with(userJwt(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("POST /desktop – returns 401 without JWT")
    void createDesktop_noJwt_returns401() throws Exception {
        mockMvc.perform(post("/desktop").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /desktops – admin only
    // -------------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("GET /desktops – returns array with admin JWT")
    void getAllDesktops_adminJwt_returns200() throws Exception {
        mockMvc.perform(get("/desktops").with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(6)
    @DisplayName("GET /desktops – returns 401 without JWT")
    void getAllDesktops_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/desktops")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /desktop/{id}
    // -------------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("GET /desktop/{id} – returns 404 for unknown ID")
    void getDesktopById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/desktop/999999").with(adminJwt(999))).andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    @DisplayName("GET /desktop/{id} – returns desktop for known ID")
    void getDesktopById_known_returns200() throws Exception {
        assertNotNull(createdDesktopId, "Desktop creation test did not produce an ID");
        mockMvc.perform(get("/desktop/" + createdDesktopId).with(adminJwt(999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdDesktopId));
    }
}
