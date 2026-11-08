package de.frauas.design.backend.auth.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionExceptionHandler")
class SessionExceptionHandlerTest {

    @Test
    @DisplayName("renders the status and OAuth2 error of the exception")
    void rendersError() {
        ResponseEntity<Map<String, Object>> response =
                new SessionExceptionHandler().handleSessionException(new TokenRevokedException());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody())
                .containsEntry("error", "token_revoked")
                .containsEntry("error_description", "The access token has been revoked");
    }
}
