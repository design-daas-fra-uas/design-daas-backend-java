package de.frauas.design.backend.integration;

import com.jayway.jsonpath.JsonPath;
import de.frauas.design.backend.auth.repository.AccessTokenRepository;
import de.frauas.design.backend.auth.repository.RefreshTokenRepository;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.repository.UserRepository;
import de.frauas.design.backend.user.service.UserService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression tests for the lockout persistence, logout/revocation and
 * MVC error-mapping fixes.
 */
@DisplayName("Auth hardening integration")
class AuthHardeningIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Hardening1!";
    private static final String LOCK_EMAIL = "lockout.test@example.com";
    private static final String LOGOUT_EMAIL = "logout.test@example.com";
    private static final String DISABLE_EMAIL = "disable.test@example.com";

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserService userService;

    @Autowired
    AccessTokenRepository accessTokenRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @BeforeAll
    void createUsers() {
        for (String email : new String[] {LOCK_EMAIL, LOGOUT_EMAIL, DISABLE_EMAIL}) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
            User user = new User();
            user.setName("Hardening Tester");
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setEnabled(true);
            userRepository.save(user);
        }
    }

    @AfterAll
    void cleanup() {
        for (String email : new String[] {LOCK_EMAIL, LOGOUT_EMAIL, DISABLE_EMAIL}) {
            userRepository.findByEmail(email).ifPresent(u -> {
                deleteTokensOf(u.getId());
                userRepository.deleteById(u.getId());
            });
        }
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", email)
                        .param("password", password))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("failed attempts are persisted and lock the account after 5 failures")
    void failedLogins_lockAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/oauth2/user/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "password")
                            .param("username", LOCK_EMAIL)
                            .param("password", "Wrong-password1"))
                    .andExpect(status().isUnauthorized());
        }

        BaseUser stored = userRepository.findByEmail(LOCK_EMAIL).orElseThrow();
        assertThat(stored.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(stored.getLockedUntil()).isNotNull();

        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", LOCK_EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("account_locked"));
    }

    @Test
    @DisplayName("logout revokes the access token and the refresh token")
    void logout_revokesTokens() throws Exception {
        String body = login(LOGOUT_EMAIL, PASSWORD);
        String accessToken = JsonPath.read(body, "$.access_token");
        String refreshToken = JsonPath.read(body, "$.refresh_token");

        mockMvc.perform(get("/oauth2/user/session").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/user/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/oauth2/user/session").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/user/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("logout without authentication is rejected")
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/oauth2/user/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("disabling a user revokes all of their tokens")
    void disableUser_revokesAllTokens() throws Exception {
        String body = login(DISABLE_EMAIL, PASSWORD);
        String refreshToken = JsonPath.read(body, "$.refresh_token");
        Integer userId = userRepository.findByEmail(DISABLE_EMAIL).orElseThrow().getId();

        userService.disableUser(userId);

        assertThat(refreshTokenRepository.findAll())
                .filteredOn(t -> t.getTokenValue().equals(refreshToken))
                .singleElement()
                .satisfies(t -> assertThat(t.isRevoked()).isTrue());
    }

    @Test
    @DisplayName("malformed JSON body returns 400, not 500")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("missing required request parameter returns 400, not 500")
    void missingParameter_returns400() throws Exception {
        mockMvc.perform(post("/oauth2/user/token").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unsupported HTTP method returns 405, not 500")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(put("/users").with(adminJwt(1))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("type mismatch in a path variable returns 400, not 500")
    void pathVariableTypeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/desktop/not-a-number").with(adminJwt(1))).andExpect(status().isBadRequest());
    }
}
