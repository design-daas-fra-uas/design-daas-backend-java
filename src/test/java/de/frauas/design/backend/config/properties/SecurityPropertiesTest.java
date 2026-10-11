package de.frauas.design.backend.config.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityProperties")
class SecurityPropertiesTest {

    @Test
    @DisplayName("app.security.swagger-public can be bound")
    void swaggerPublic_isBindable() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("app.security.swagger-public", "true")));

        SecurityProperties props =
                binder.bind("app.security", SecurityProperties.class).get();

        assertThat(props.isSwaggerPublic()).isTrue();
    }

    @Test
    @DisplayName("swagger is not public by default")
    void swaggerPublic_defaultsToFalse() {
        assertThat(new SecurityProperties().isSwaggerPublic()).isFalse();
    }
}
