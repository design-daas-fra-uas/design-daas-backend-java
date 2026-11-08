package de.frauas.design.backend.bootstrap;

import de.frauas.design.backend.config.properties.AdminInitializerProperties;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes application data required at startup.
 *
 * <p>When enabled, creates the configured administrator if no user with
 * the configured email address exists.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminInitializerProperties properties;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Admin initialization is disabled");
            return;
        }

        createDefaultAdmin();
    }

    private void createDefaultAdmin() {
        String email = properties.getEmail();

        if (userRepository.existsByEmail(email)) {
            log.debug("Admin initialization skipped; user already exists");
            return;
        }

        Admin admin = new Admin();
        admin.setName("Default Admin");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(properties.getPassword()));
        admin.setEnabled(true);

        userRepository.save(admin);

        log.info("Created default administrator account: {}", LogMasking.maskEmail(email));
    }
}
