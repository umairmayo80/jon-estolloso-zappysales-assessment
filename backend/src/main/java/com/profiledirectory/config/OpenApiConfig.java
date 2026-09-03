package com.profiledirectory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI profileDirectoryOpenApi() {
        Components components = new Components()
                .addSecuritySchemes("cookieAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("PD_ACCESS")
                        .description("HttpOnly access JWT cookie set by the login endpoint."))
                .addSecuritySchemes("csrfHeader", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")
                        .description("Required on every unsafe request; copy the XSRF-TOKEN cookie value."))
                .addSecuritySchemes("refreshCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("PD_REFRESH")
                        .description("HttpOnly rotating refresh-token cookie used only by refresh and logout."))
                .addSchemas("ProblemDetail", problemDetailSchema());

        addProblemResponse(components, "BadRequest", "400", "INVALID_REQUEST", "The request could not be understood.");
        addProblemResponse(components, "Unauthenticated", "401", "UNAUTHENTICATED", "Authentication is required.");
        addProblemResponse(components, "Forbidden", "403", "FORBIDDEN", "You are not allowed to perform this action.");
        addProblemResponse(components, "NotFound", "404", "NOT_FOUND", "The requested resource was not found.");
        addProblemResponse(components, "Conflict", "409", "CONFLICT", "The request conflicts with an existing record.");
        addProblemResponse(components, "PreconditionFailed", "412", "STALE_VERSION", "The record changed since it was loaded.");
        addProblemResponse(components, "PayloadTooLarge", "413", "PAYLOAD_TOO_LARGE", "The request body exceeds the configured limit.");
        addProblemResponse(components, "ValidationFailed", "422", "VALIDATION_FAILED", "One or more fields are invalid.");
        addProblemResponse(components, "PreconditionRequired", "428", "PRECONDITION_REQUIRED", "A current If-Match header is required.");
        addProblemResponse(components, "RateLimited", "429", "RATE_LIMITED", "Too many authentication attempts.");
        addProblemResponse(components, "InternalServerError", "500", "INTERNAL_ERROR", "An unexpected error occurred.");

        return new OpenAPI()
                .info(new Info().title("Profile Directory API").version("v1").description("Admin-only user-profile and address management API."))
                .components(components);
    }

    /**
     * Applies the shared RFC 9457 failures to every operation. Controllers retain their generated
     * success responses while clients get one consistent, component-backed error contract.
     */
    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((pathTemplate, path) -> path.readOperationsMap().forEach((method, operation) -> {
                ApiResponses responses = operation.getResponses();
                if (responses == null) {
                    responses = new ApiResponses();
                    operation.setResponses(responses);
                }
                addResponseReference(responses, "400", "BadRequest");
                addResponseReference(responses, "401", "Unauthenticated");
                addResponseReference(responses, "403", "Forbidden");
                addResponseReference(responses, "404", "NotFound");
                addResponseReference(responses, "409", "Conflict");
                addResponseReference(responses, "412", "PreconditionFailed");
                addResponseReference(responses, "413", "PayloadTooLarge");
                addResponseReference(responses, "422", "ValidationFailed");
                addResponseReference(responses, "428", "PreconditionRequired");
                addResponseReference(responses, "429", "RateLimited");
                addResponseReference(responses, "default", "InternalServerError");
                applySecurity(pathTemplate, method, operation);
            }));
        };
    }

    private static void applySecurity(String path, PathItem.HttpMethod method, io.swagger.v3.oas.models.Operation operation) {
        if (path.startsWith("/api/v1/users")) {
            SecurityRequirement requirements = new SecurityRequirement().addList("cookieAuth");
            if (isUnsafe(method)) {
                requirements.addList("csrfHeader");
            }
            operation.setSecurity(List.of(requirements));
            return;
        }

        if (!path.startsWith("/api/v1/auth")) {
            return;
        }

        SecurityRequirement requirements = new SecurityRequirement();
        if ("/api/v1/auth/me".equals(path)) {
            requirements.addList("cookieAuth");
        } else if (isUnsafe(method)) {
            if (!"/api/v1/auth/login".equals(path)) {
                requirements.addList("refreshCookie");
            }
            requirements.addList("csrfHeader");
        }
        if (!requirements.isEmpty()) {
            operation.setSecurity(List.of(requirements));
        }
    }

    private static boolean isUnsafe(PathItem.HttpMethod method) {
        return switch (method) {
            case POST, PUT, PATCH, DELETE -> true;
            default -> false;
        };
    }

    private static ObjectSchema problemDetailSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.description("RFC 9457 problem response. `code` and `traceId` are stable extensions; `fieldErrors` is present for validation failures.");
        schema.addProperties("type", new StringSchema().format("uri-reference").example("https://profile-directory.local/problems/validation_failed"));
        schema.addProperties("title", new StringSchema().example("Unprocessable Content"));
        schema.addProperties("status", new IntegerSchema().example(422));
        schema.addProperties("detail", new StringSchema().example("One or more fields are invalid"));
        schema.addProperties("instance", new StringSchema().format("uri-reference").example("/api/v1/users"));
        schema.addProperties("code", new StringSchema().example("VALIDATION_FAILED"));
        schema.addProperties("traceId", new StringSchema().example("8c82f72d-8c20-4e35-b5b3-a63fd0583784"));
        ObjectSchema fieldErrors = new ObjectSchema();
        fieldErrors.description("A map from input field names to client-safe validation messages.");
        fieldErrors.example(Map.of("email", "must be a well-formed email address"));
        schema.addProperties("fieldErrors", fieldErrors);
        schema.required(java.util.List.of("type", "title", "status", "detail", "instance", "code", "traceId"));
        return schema;
    }

    private static void addProblemResponse(Components components, String name, String status, String code, String detail) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("type", "https://profile-directory.local/problems/" + code.toLowerCase());
        example.put("title", titleFor(status));
        example.put("status", Integer.parseInt(status));
        example.put("detail", detail);
        example.put("instance", "/api/v1/users");
        example.put("code", code);
        example.put("traceId", "8c82f72d-8c20-4e35-b5b3-a63fd0583784");
        if ("422".equals(status)) {
            example.put("fieldErrors", Map.of("email", "must be a well-formed email address"));
        }

        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))
                .example(example);
        components.addResponses(name, new ApiResponse()
                .description(titleFor(status))
                .content(new Content().addMediaType("application/problem+json", mediaType)));
    }

    private static void addResponseReference(ApiResponses responses, String responseCode, String componentName) {
        if (!responses.containsKey(responseCode)) {
            responses.addApiResponse(responseCode, new ApiResponse().$ref("#/components/responses/" + componentName));
        }
    }

    private static String titleFor(String status) {
        return switch (status) {
            case "400" -> "Bad Request";
            case "401" -> "Unauthorized";
            case "403" -> "Forbidden";
            case "404" -> "Not Found";
            case "409" -> "Conflict";
            case "412" -> "Precondition Failed";
            case "413" -> "Payload Too Large";
            case "422" -> "Unprocessable Content";
            case "428" -> "Precondition Required";
            case "429" -> "Too Many Requests";
            default -> "Internal Server Error";
        };
    }
}
