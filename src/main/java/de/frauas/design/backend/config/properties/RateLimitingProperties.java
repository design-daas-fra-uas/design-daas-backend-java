package de.frauas.design.backend.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration for API rate limiting and brute-force protection.
 *
 * <p>Limits are applied per client IP address using a sliding
 * token-bucket strategy. Rate limiting can be disabled for tests
 * or other environments through {@code app.rate-limiting.enabled}.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.rate-limiting")
public class RateLimitingProperties {

    /**
     * Enables or disables rate limiting.
     */
    private boolean enabled = true;

    /**
     * Maximum number of authentication-token requests per minute per IP.
     */
    @Min(1)
    private int authTokenRequestsPerMinute = 20;

    /**
     * Maximum number of registration requests per minute per IP.
     */
    @Min(1)
    private int registerRequestsPerMinute = 5;

    /**
     * Maximum number of email-validation requests per minute per IP.
     */
    @Min(1)
    private int validateEmailRequestsPerMinute = 10;

    /**
     * Maximum number of resend-registration-code requests per minute per IP.
     */
    @Min(1)
    private int resendCodeRequestsPerMinute = 3;

    /**
     * CIDR ranges of trusted reverse proxies.
     *
     * <p>{@code X-Forwarded-For} must only be trusted when the request
     * originates from one of these proxy ranges. Requests from other
     * sources should use the direct remote address to prevent IP spoofing.</p>
     */
    private List<String> trustedProxyRanges = List.of("127.0.0.1/32", "::1/128");
}
