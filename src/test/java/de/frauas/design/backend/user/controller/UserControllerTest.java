package de.frauas.design.backend.user.controller;

import de.frauas.design.backend.auth.service.JwtUserResolver;
import de.frauas.design.backend.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    @Mock
    UserService userService;

    @Mock
    JwtUserResolver jwtUserResolver;

    @InjectMocks
    UserController controller;

    @Test
    @DisplayName("request-application asks the service to mail the admins for the caller")
    void requestApplication() {
        Jwt jwt = mock(Jwt.class);
        when(jwtUserResolver.resolveFromAuthenticatedJwt(jwt)).thenReturn(42);

        var response = controller.requestApplication("LibreOffice", "DE", jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).requestApplication(42, "LibreOffice");
    }
}
