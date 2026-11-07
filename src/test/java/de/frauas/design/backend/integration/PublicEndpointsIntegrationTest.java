package de.frauas.design.backend.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for endpoints that must work without a token (health),
 * and for the content types accepted by the token endpoint.
 */
@DisplayName("Public endpoints Integration")
class PublicEndpointsIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /actuator/health – reachable without a token, no component details")
    void health_withoutToken_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("POST /oauth2/user/token – JSON body is rejected with 415 instead of being ignored")
    void token_jsonBody_returns415() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"password\",\"username\":\"a@b.c\",\"password\":\"x\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
