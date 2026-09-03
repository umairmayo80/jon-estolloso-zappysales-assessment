package com.profiledirectory.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
    @Test
    void sharedProblemSchemaRoundTripsThroughTheSwaggerSerializer() {
        OpenAPI openApi = new OpenApiConfig().profileDirectoryOpenApi();

        assertThatCode(() -> Json.mapper().readValue(Json.mapper().writeValueAsString(openApi), OpenAPI.class))
                .doesNotThrowAnyException();
    }
}
