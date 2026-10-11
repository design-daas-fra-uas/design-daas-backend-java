package de.frauas.design.backend.integration;

import de.frauas.design.backend.auth.model.AccessTokenEntity;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for {@code GET /oauth2/user/session} and for the global
 * revocation-enforcement filter ({@code TokenRevocationValidator}) wired into the
 * resource-server's JWT decoder.
 *
 * <p>Unlike other controller integration tests, these tests deliberately obtain a
 * <b>real</b> signed JWT via {@code POST /oauth2/user/token} (rather than
 * {@link MockJwt}'s {@code SecurityMockMvcRequestPostProcessors.jwt()} shortcut),
 * because {@code MockJwt} bypasses the real {@code JwtDecoder} entirely and would
 * never exercise {@code TokenRevocationValidator}.</p>
 */
@DisplayName("OAuth2Session + revocation enforcement Integration")
class OAuth2SessionControllerIntegrationTest extends BaseIntegrationTest {

    private static final String EMAIL = "session.test@example.com";
    private static final String PASSWORD = "Session1234!";

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
        testUser.setName("Session Tester");
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

    /** Performs a real password grant and returns the issued access token string. */
    private String obtainAccessToken() throws Exception {
        String response = mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String key = "\"access_token\":\"";
        int start = response.indexOf(key) + key.length();
        int end = response.indexOf("\"", start);
        return response.substring(start, end);
    }

    // -------------------------------------------------------------------------
    // GET /oauth2/user/session
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("session – no Authorization header returns 401")
    void session_noToken_returns401() throws Exception {
        mockMvc.perform(get("/oauth2/user/session")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("session – valid access token returns user details")
    void session_validToken_returnsUserDetails() throws Exception {
        String accessToken = obtainAccessToken();

        mockMvc.perform(get("/oauth2/user/session").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(testUser.getId()))
                .andExpect(jsonPath("$.name").value(testUser.getName()));
    }

    @Test
    @DisplayName("session – revoked access token is rejected before reaching the controller (401, no 500)")
    void session_revokedToken_returns401NotServerError() throws Exception {
        String accessToken = obtainAccessToken();
        String jti = extractJtiFromJwt(accessToken);

        AccessTokenEntity entity = accessTokenRepository.findByJti(jti).orElseThrow();
        entity.revoke();
        accessTokenRepository.save(entity);

        mockMvc.perform(get("/oauth2/user/session").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Global revocation enforcement (TokenRevocationValidator) on other endpoints
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("disabling a user invalidates their already-issued access token on the next request")
    void disablingUser_invalidatesExistingAccessToken() throws Exception {
        String accessToken = obtainAccessToken();

        try {
            testUser.setEnabled(false);
            userRepository.save(testUser);

            mockMvc.perform(get("/users/access/1").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        } finally {
            testUser.setEnabled(true);
            userRepository.save(testUser);
        }
    }

    /** Extracts the {@code jti} claim from the JWT payload (middle base64 segment). */
    private String extractJtiFromJwt(String jwt) {
        String payload = jwt.split("\\.")[1];
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        String key = "\"jti\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
