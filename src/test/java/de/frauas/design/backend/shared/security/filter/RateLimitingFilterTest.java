package de.frauas.design.backend.shared.security.filter;

import de.frauas.design.backend.config.properties.RateLimitingProperties;
import de.frauas.design.backend.shared.security.ip.ClientIpResolver;
import de.frauas.design.backend.shared.security.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitingFilter")
class RateLimitingFilterTest {

    private RateLimitingProperties properties;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitingProperties();
        properties.setAuthTokenRequestsPerMinute(1);
        properties.setRegisterRequestsPerMinute(1);
        properties.setValidateEmailRequestsPerMinute(1);
        properties.setResendCodeRequestsPerMinute(1);
        filter = new RateLimitingFilter(properties, new RateLimiter(), new ClientIpResolver(properties));
    }

    private MockHttpServletResponse call(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/oauth2/user/token", "/user", "/user/validate_email", "/user/resend_code"})
    @DisplayName("answers 429 with Retry-After once the limit is used up")
    void limitsProtectedPaths(String path) throws Exception {
        assertThat(call("POST", path).getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call("POST", path);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString()).contains("too_many_requests");
    }

    @Test
    @DisplayName("does not limit other paths or methods")
    void ignoresOtherRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/users").getStatus()).isEqualTo(200);
            assertThat(call("GET", "/user").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("does nothing when rate limiting is disabled")
    void disabled() throws Exception {
        properties.setEnabled(false);
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/user").getStatus()).isEqualTo(200);
        }
    }
}
