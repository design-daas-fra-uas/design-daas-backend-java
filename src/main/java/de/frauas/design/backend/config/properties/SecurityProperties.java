package de.frauas.design.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-specific security configuration.
 *
 * <p>Properties are prefixed with {@code app.security}.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /**
     * Whether Swagger UI and the generated OpenAPI documentation are
     * accessible without authentication.
     */
    private boolean swaggerPublic = false;
}
