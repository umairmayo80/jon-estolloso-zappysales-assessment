package com.profiledirectory.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionRequiredPropertiesEnvironmentPostProcessorTest {
    private final ProductionRequiredPropertiesEnvironmentPostProcessor processor =
            new ProductionRequiredPropertiesEnvironmentPostProcessor();

    @Test
    void productionFailsBeforeStartupWhenRequiredSecretValuesAreMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL")
                .hasMessageContaining("APP_REFRESH_TOKEN_PEPPER");
    }

    @Test
    void productionAcceptsAnExplicitCompleteEnvironmentContract() {
        MockEnvironment environment = configured("prod");

        assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void productionRejectsAnUnsafeCombinedDevelopmentProfile() {
        MockEnvironment environment = configured("prod", "dev");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be combined")
                .hasMessageContaining("dev");
    }

    @Test
    void nonProductionProfilesDoNotRequireProductionSecrets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThatCode(() -> processor.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    private MockEnvironment configured(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        environment.setProperty("DATABASE_URL", "jdbc:postgresql://db/profile_directory");
        environment.setProperty("DATABASE_USERNAME", "profile_directory");
        environment.setProperty("DATABASE_PASSWORD", "secret");
        environment.setProperty("APP_JWT_ISSUER", "https://profiles.example.test");
        environment.setProperty("APP_JWT_PRIVATE_KEY_PATH", "file:/run/secrets/jwt-private.pem");
        environment.setProperty("APP_JWT_PUBLIC_KEY_PATH", "file:/run/secrets/jwt-public.pem");
        environment.setProperty("APP_REFRESH_TOKEN_PEPPER", "long-random-secret");
        return environment;
    }
}
