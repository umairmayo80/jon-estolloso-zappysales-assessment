package com.profiledirectory.config;

import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.shared.web.InputNormalizer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates an initial local administrator once, only when explicitly enabled by profile/env. */
@Component
@Order(1)
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final AppSecurityProperties properties;
    private final AdminAccountRepository admins;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            AppSecurityProperties properties,
            AdminAccountRepository admins,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppSecurityProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        String email = InputNormalizer.email(bootstrap.getAdminEmail());
        String password = bootstrap.getAdminPassword();
        if (email == null || email.isBlank() || password == null || password.length() < 12) {
            throw new IllegalStateException("Bootstrap admin requires an email and a password of at least 12 characters");
        }
        if (admins.findByEmail(email).isEmpty()) {
            admins.save(new AdminAccount(email, passwordEncoder.encode(password), InputNormalizer.required(bootstrap.getAdminDisplayName())));
        }
    }
}
