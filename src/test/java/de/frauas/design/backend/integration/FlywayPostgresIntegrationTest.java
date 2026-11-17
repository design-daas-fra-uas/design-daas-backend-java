package de.frauas.design.backend.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations against PostgreSQL, as in production, and lets Hibernate
 * validate the entities against the resulting schema ({@code ddl-auto: validate}).
 *
 * <p>The other tests use H2 with a schema generated from the entities, so a broken
 * migration would only show up here. Needs Docker; skipped without it.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway migrations on PostgreSQL")
class FlywayPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    Flyway flyway;

    @Test
    @DisplayName("applies every migration and the entities match the schema")
    void migrationsApplyAndSchemaValidates() throws IOException {
        var applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied[applied.length - 1].getVersion()).isEqualTo(latestMigrationVersion());
        assertThat(flyway.info().pending()).isEmpty();
    }

    /**
     * Highest version among the scripts in {@code db/migration}, so the test does not need
     * to change when a new migration is added.
     */
    private static MigrationVersion latestMigrationVersion() throws IOException {
        Resource[] scripts = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql");
        return Arrays.stream(scripts)
                .map(Resource::getFilename)
                .map(name -> MigrationVersion.fromVersion(name.substring(1, name.indexOf("__"))))
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }
}
