package de.frauas.design.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>{@code @EnableScheduling} activates the {@link de.frauas.design.backend.auth.service.TokenCleanupService}
 * scheduled job. {@code @EnableAsync} lets {@link de.frauas.design.backend.user.service.MailService} send
 * emails on a background thread so slow/unreachable SMTP servers never block request-handling threads.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class DesignBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(DesignBackendApplication.class, args);
    }
}
