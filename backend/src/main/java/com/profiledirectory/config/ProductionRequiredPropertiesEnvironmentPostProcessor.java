package com.profiledirectory.config;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Refuses to boot a production process with development fallbacks or blank secret values.
 *
 * <p>The normal property placeholders intentionally make the deployment contract visible in
 * {@code application-prod.properties}. This early check produces a deterministic, actionable
 * failure before datasource, Liquibase, or HTTP infrastructure starts.</p>
 */
public final class ProductionRequiredPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "DATABASE_URL",
            "DATABASE_USERNAME",
            "DATABASE_PASSWORD",
            "APP_JWT_ISSUER",
            "APP_JWT_PRIVATE_KEY_PATH",
            "APP_JWT_PUBLIC_KEY_PATH",
            "APP_REFRESH_TOKEN_PEPPER");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        List<String> incompatible = List.of("dev", "test").stream()
                .filter(profile -> environment.acceptsProfiles(Profiles.of(profile)))
                .toList();
        if (!incompatible.isEmpty()) {
            throw new IllegalStateException("Production must not be combined with profile(s): "
                    + String.join(", ", incompatible));
        }

        List<String> missing = REQUIRED_PROPERTIES.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production configuration is missing required environment/secret values: "
                    + missing.stream().collect(Collectors.joining(", ")));
        }
    }

    @Override
    public int getOrder() {
        // ConfigDataEnvironmentPostProcessor has already loaded profile-specific properties.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
