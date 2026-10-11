package de.frauas.design.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the application-wide {@link Clock} so time-dependent logic (token expiry,
 * account lockout, registration-code validity) can be tested with a fixed clock
 * instead of calling {@code Instant.now()} directly.
 */
@Configuration
public class ClockConfig {

    /**
     * Creates the UTC system clock used by all time-based services.
     *
     * @return the system clock in UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
