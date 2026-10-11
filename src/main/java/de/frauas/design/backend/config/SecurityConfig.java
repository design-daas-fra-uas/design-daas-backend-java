package de.frauas.design.backend.config;

import de.frauas.design.backend.config.properties.SecurityProperties;
import de.frauas.design.backend.shared.security.Authorities;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configures stateless JWT-based security for the API.
 *
 * <p>The API uses OAuth2 Resource Server authentication with JWTs.
 * Authentication is required by default; only explicitly configured
 * public endpoints bypass authentication.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_ENDPOINTS = {"/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"};

    private final JwtDecoder jwtDecoder;
    private final CorsConfigurationSource corsConfigurationSource;
    private final SecurityProperties securityProperties;

    /**
     * Configures the API security filter chain.
     *
     * @param http Spring Security HTTP configuration
     * @return configured security filter chain
     */
    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/user").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/user/validate_email")
                            .permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/user/resend_code").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/oauth2/user/token").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/version").permitAll();

                    if (securityProperties.isSwaggerPublic()) {
                        auth.requestMatchers(SWAGGER_ENDPOINTS).permitAll();
                    } else {
                        auth.requestMatchers(SWAGGER_ENDPOINTS).hasAuthority(Authorities.SCOPE_ADMIN);
                    }

                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }
}
