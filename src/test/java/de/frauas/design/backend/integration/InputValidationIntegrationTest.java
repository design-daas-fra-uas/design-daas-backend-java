package de.frauas.design.backend.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static de.frauas.design.backend.integration.MockJwt.userJwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Input-validation limits (field sizes, password bounds, path variables) and the
 * resend-code endpoint.
 */
@DisplayName("Input validation integration")
class InputValidationIntegrationTest extends BaseIntegrationTest {

    private String userBody(String name, String email, String password) {
        return "{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password);
    }

    @Test
    @DisplayName("POST /user rejects a name longer than 255 characters")
    void createUser_oversizedName_returns400() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("x".repeat(256), "long.name@example.com", "Valid1234")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /user rejects a password longer than 72 characters")
    void createUser_passwordTooLong_returns400() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("Alice", "long.pw@example.com", "Aa1" + "x".repeat(70))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /user/validate_email rejects a missing body field")
    void validateEmail_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/user/validate_email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"registration_code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /user-groups rejects an oversized name")
    void createUserGroup_oversizedName_returns400() throws Exception {
        mockMvc.perform(post("/user_group")
                        .with(adminJwt(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("request-application rejects an application name with illegal characters")
    void requestApplication_invalidPathVariable_returns400() throws Exception {
        mockMvc.perform(post("/user/request-application/{app}/{iso}", "bad<script>", "DE")
                        .with(userJwt(1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /user/resend_code answers 202 for an unknown e-mail without authentication")
    void resendCode_unknownEmail_returns202() throws Exception {
        mockMvc.perform(post("/user/resend_code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody.here@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /user/resend_code rejects a malformed e-mail")
    void resendCode_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/user/resend_code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }
}
