package de.frauas.design.backend.shared.security.filter;

import de.frauas.design.backend.config.properties.RateLimitingProperties;
import de.frauas.design.backend.shared.security.ip.ClientIpResolver;
import de.frauas.design.backend.shared.security.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies IP-based rate limiting to sensitive unauthenticated endpoints.
 *
 * <p>The filter executes before Spring Security so excessive requests are
 * rejected with HTTP 429 before any controller or Spring Security logic runs.</p>
 *
 * <p>The filter is ordered at -200 (before the Spring Security filter chain at
 * -100) so rate-limited requests are dropped at the earliest possible stage.</p>
 *
 * <p>Disable via {@code app.rate-limiting.enabled=false} (e.g. in test profiles).</p>
 */
@Component
@Order(-200)
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AUTH_TOKEN_PATH = "/oauth2/user/token";
    private static final String REGISTER_PATH = "/user";
    private static final String VALIDATE_EMAIL_PATH = "/user/validate_email";
    private static final String RESEND_CODE_PATH = "/user/resend_code";

    private final RateLimitingProperties properties;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        return !HttpMethod.POST.matches(request.getMethod()) || !isRateLimitedPath(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        String clientIp = clientIpResolver.resolve(request);

        int limit = limitFor(path);

        if (rateLimiter.tryConsume(clientIp, path, limit)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Rate limit exceeded for endpoint {}", path);

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        response.getWriter().write("""
            {
                "error": "too_many_requests",
                "error_description": "Too many requests. Please try again later."
            }
            """);
    }

    private boolean isRateLimitedPath(String path) {
        return AUTH_TOKEN_PATH.equals(path)
                || REGISTER_PATH.equals(path)
                || VALIDATE_EMAIL_PATH.equals(path)
                || RESEND_CODE_PATH.equals(path);
    }

    private int limitFor(String path) {
        return switch (path) {
            case AUTH_TOKEN_PATH -> properties.getAuthTokenRequestsPerMinute();

            case REGISTER_PATH -> properties.getRegisterRequestsPerMinute();

            case VALIDATE_EMAIL_PATH -> properties.getValidateEmailRequestsPerMinute();

            case RESEND_CODE_PATH -> properties.getResendCodeRequestsPerMinute();

            default -> throw new IllegalArgumentException("Unsupported rate-limited path: " + path);
        };
    }
}
