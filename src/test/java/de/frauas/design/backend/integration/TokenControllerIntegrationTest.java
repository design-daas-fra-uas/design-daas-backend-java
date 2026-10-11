package de.frauas.design.backend.integration;

import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /oauth2/user/token.
 * Tests password-grant and refresh-token grant flows end-to-end against H2.
 */
@DisplayName("TokenController Integration")
class TokenControllerIntegrationTest extends BaseIntegrationTest {

    private static final String EMAIL = "token.test@example.com";
    private static final String PASSWORD = "Token1234!";

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    AccessTokenRepository accessTokenRepository;

    private User testUser;

    @BeforeAll
    void setupUser() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        testUser = new User();
        testUser.setName("Token Tester");
        testUser.setEmail(EMAIL);
        testUser.setPassword(passwordEncoder.encode(PASSWORD));
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);
    }

    @AfterAll
    void cleanup() {
        deleteTokensOf(testUser.getId());
        userRepository.deleteById(testUser.getId());
    }

    // -------------------------------------------------------------------------
    // Password grant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("password grant – valid credentials returns access + refresh token")
    void passwordGrant_validCredentials_returns200WithTokens() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber());
    }

    @Test
    @DisplayName("password grant – access token is persisted to access_tokens table")
    void passwordGrant_accessTokenIsPersisted() throws Exception {
        String response = mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Decode the JWT and extract jti
        String accessToken = extractJsonField(response, "access_token");
        String jtiFromJwt = extractJtiFromJwt(accessToken);

        assertTrue(
                accessTokenRepository.findByJti(jtiFromJwt).isPresent(),
                "Issued access token must be persisted with its jti");
    }

    @Test
    @DisplayName("password grant – wrong password returns 401")
    void passwordGrant_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", EMAIL)
                        .param("password", "WrongPassword1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("password grant – unknown user returns 401")
    void passwordGrant_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "ghost@nowhere.com")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("password grant – disabled user returns 401")
    void passwordGrant_disabledUser_returns401() throws Exception {
        testUser.setEnabled(false);
        userRepository.save(testUser);

        try {
            mockMvc.perform(post("/oauth2/user/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "password")
                            .param("username", EMAIL)
                            .param("password", PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_grant"));
        } finally {
            testUser.setEnabled(true);
            userRepository.save(testUser);
        }
    }

    @Test
    @DisplayName("password grant – missing username returns 400")
    void passwordGrant_missingUsername_returns400() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    // -------------------------------------------------------------------------
    // Refresh-token grant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refresh grant – valid refresh token returns new access token")
    void refreshGrant_validToken_returnsNewAccessToken() throws Exception {
        String response = mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = extractJsonField(response, "refresh_token");

        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("refresh grant – refresh token is rotated (old token rejected after use)")
    void refreshGrant_tokenIsRotated_oldTokenRejected() throws Exception {
        // Step 1: obtain initial tokens
        String firstResponse = mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalRefreshToken = extractJsonField(firstResponse, "refresh_token");

        // Step 2: consume the refresh token once
        String refreshResponse = mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", originalRefreshToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newRefreshToken = extractJsonField(refreshResponse, "refresh_token");
        assertNotEquals(originalRefreshToken, newRefreshToken, "Refresh token must be rotated");

        // Step 3: attempt to reuse the original (now revoked) refresh token
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", originalRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("refresh grant – invalid token returns 401")
    void refreshGrant_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", "this-is-not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("refresh grant – missing refresh_token param returns 400")
    void refreshGrant_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    // -------------------------------------------------------------------------
    // Unsupported grant type
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("unsupported grant type returns 400")
    void unsupportedGrantType_returns400() throws Exception {
        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /**
     * Extracts the {@code jti} claim from the JWT payload (middle base64 segment).
     * Avoids a dependency on a JWT library in tests.
     */
    private String extractJtiFromJwt(String jwt) {
        String payload = jwt.split("\\.")[1];
        // base64url decode
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        // Extract jti field
        String key = "\"jti\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
