package com.profiledirectory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class DevProfileConfigurationTest {

    @Test
    void defaultsTheBootstrapAdministratorDisplayNameToSardarUmair() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application-dev.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("app.bootstrap.admin-display-name"))
                .isEqualTo("${APP_BOOTSTRAP_ADMIN_DISPLAY_NAME:Sardar Umair}");
    }
}
