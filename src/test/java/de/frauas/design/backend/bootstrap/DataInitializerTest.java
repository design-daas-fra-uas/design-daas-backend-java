package de.frauas.design.backend.bootstrap;

import de.frauas.design.backend.config.properties.AdminInitializerProperties;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataInitializer")
class DataInitializerTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    AdminInitializerProperties properties;
    DataInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new AdminInitializerProperties();
        properties.setEmail("admin@design.local");
        properties.setPassword("Secret123");
        initializer = new DataInitializer(userRepository, passwordEncoder, properties);
    }

    @Test
    @DisplayName("does nothing when disabled")
    void disabled() {
        properties.setEnabled(false);

        initializer.run(new DefaultApplicationArguments());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("skips an admin that already exists")
    void existingAdmin() {
        properties.setEnabled(true);
        when(userRepository.existsByEmail("admin@design.local")).thenReturn(true);

        initializer.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("creates an enabled admin with the encoded password")
    void createsAdmin() {
        properties.setEnabled(true);
        when(userRepository.existsByEmail("admin@design.local")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("hash");

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@design.local");
        assertThat(saved.getValue().getPassword()).isEqualTo("hash");
        assertThat(saved.getValue().isEnabled()).isTrue();
    }
}
