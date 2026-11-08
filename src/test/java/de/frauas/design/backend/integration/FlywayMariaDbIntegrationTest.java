package de.frauas.design.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations against MariaDB, as in production, and lets Hibernate
 * validate the entities against the resulting schema ({@code ddl-auto: validate}).
 *
 * <p>The other tests use H2 with a schema generated from the entities, so a broken
 * migration would only show up here. Needs Docker; skipped without it.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway migrations on MariaDB")
class FlywayMariaDbIntegrationTest {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4");

    @DynamicPropertySource
    static void mariadbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    Flyway flyway;

    @Test
    @DisplayName("applies every migration and the entities match the schema")
    void migrationsApplyAndSchemaValidates() {
        var applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied[applied.length - 1].getVersion().getVersion()).isEqualTo("4");
        assertThat(flyway.info().pending()).isEmpty();
    }
}
