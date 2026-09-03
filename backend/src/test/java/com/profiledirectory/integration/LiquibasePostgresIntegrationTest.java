package com.profiledirectory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.profiledirectory.ProfileDirectoryApplication;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ProfileDirectoryApplication.class, properties = "spring.config.import=optional:classpath:/test-no-dotenv.properties")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LiquibasePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("profile_directory_test")
            .withUsername("profile_directory")
            .withPassword("profile_directory");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private AdminAccountRepository admins;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void liquibaseCreatesCoreTablesPrimaryAddressIndexAndAppendOnlyTrigger() {
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('admin_accounts', 'user_profiles', 'addresses', 'refresh_sessions', 'audit_events')
                """, Integer.class);
        Integer primaryIndex = jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = 'uq_addresses_active_primary'", Integer.class);
        Integer auditTrigger = jdbc.queryForObject(
                "select count(*) from pg_trigger where tgname = 'trg_audit_events_immutable' and not tgisinternal", Integer.class);

        assertThat(tables).isEqualTo(5);
        assertThat(primaryIndex).isEqualTo(1);
        assertThat(auditTrigger).isEqualTo(1);
    }

    @Test
    void generatedOpenApiPublishesTheReusableRfc9457ErrorContract() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        var document = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(document.at("/components/schemas/ProblemDetail/type").asText()).isEqualTo("object");
        assertThat(document.at("/components/schemas/ProblemDetail/properties/code/type").asText()).isEqualTo("string");
        assertThat(document.at("/components/schemas/ProblemDetail/properties/fieldErrors/type").asText()).isEqualTo("object");
        assertThat(document.at("/components/responses/PreconditionRequired/content/application~1problem+json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ProblemDetail");
        assertThat(document.at("/paths/~1api~1v1~1users/get/responses/default/$ref").asText())
                .isEqualTo("#/components/responses/InternalServerError");
        var createUserSecurity = document.at("/paths/~1api~1v1~1users/post/security/0");
        assertThat(createUserSecurity.has("cookieAuth")).isTrue();
        assertThat(createUserSecurity.has("csrfHeader")).isTrue();
        assertThat(document.at("/paths/~1api~1v1~1auth~1login/post/security/0").has("csrfHeader")).isTrue();

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void csrfCookieAuthenticationAndEtagPreconditionsProtectTheUserApi() throws Exception {
        String adminEmail = "api-admin@example.test";
        if (admins.findByEmail(adminEmail).isEmpty()) {
            admins.saveAndFlush(new AdminAccount(adminEmail, passwordEncoder.encode("CorrectHorseBattery1"), "API Administrator"));
        }

        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"api-admin@example.test\",\"password\":\"CorrectHorseBattery1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("PD_ACCESS=")))
                .andReturn();
        Cookie accessCookie = login.getResponse().getCookie("PD_ACCESS");
        assertThat(accessCookie).isNotNull();

        String email = "user-" + java.util.UUID.randomUUID() + "@example.test";
        MvcResult created = mvc.perform(post("/api/v1/users")
                        .cookie(accessCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"firstName\":\"Casey\",\"lastName\":\"Jones\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();
        String userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(patch("/api/v1/users/{id}", userId)
                        .cookie(accessCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"firstName\":\"Casey\",\"lastName\":\"Jones\"}"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void csrfRejectsAnUnsafeLoginWithoutTheDoubleSubmitHeader() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"csrf-check@example.test\",\"password\":\"CorrectHorseBattery1\"}"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(errorCode(result)).isEqualTo("CSRF_INVALID");
    }

    @Test
    void apiBodyLimitRejectsOversizedJsonBeforeCsrfOrDeserialization() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(1_048_577)))
                .andExpect(status().isPayloadTooLarge())
                .andReturn();

        assertThat(errorCode(result)).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void failedLoginAuditSurvivesTheRejectedTransactionWithoutRecordingSubmittedIdentity() throws Exception {
        String knownEmail = "failed-login-" + UUID.randomUUID() + "@example.test";
        AdminAccount knownAdmin = admins.saveAndFlush(new AdminAccount(
                knownEmail, passwordEncoder.encode("CorrectHorseBattery1"), "Failed Login Administrator"));

        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        MvcResult knownFailure = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + knownEmail + "\",\"password\":\"WrongPassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String unknownEmail = "unknown-login-" + UUID.randomUUID() + "@example.test";
        MvcResult unknownFailure = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + unknownEmail + "\",\"password\":\"WrongPassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(errorCode(knownFailure)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(errorCode(unknownFailure)).isEqualTo("INVALID_CREDENTIALS");
        Integer knownAudit = jdbc.queryForObject(
                "select count(*) from audit_events where event_type = 'LOGIN_FAILED' and actor_admin_id = ?",
                Integer.class, knownAdmin.getId());
        String unknownMetadata = jdbc.queryForObject("""
                select metadata from audit_events
                where event_type = 'LOGIN_FAILED' and actor_admin_id is null and target_type = 'AUTHENTICATION'
                order by occurred_at desc limit 1
                """, String.class);
        assertThat(knownAudit).isEqualTo(1);
        assertThat(unknownMetadata).contains("invalid_credentials").doesNotContain(unknownEmail);
    }

    @Test
    void protectedApiReturnsProblemDetailsForUnauthenticatedAndNonAdminCallers() throws Exception {
        MvcResult unauthenticated = mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(errorCode(unauthenticated)).isEqualTo("UNAUTHENTICATED");

        MvcResult forbidden = mvc.perform(get("/api/v1/users").with(user("directory-viewer").roles("USER")))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(errorCode(forbidden)).isEqualTo("FORBIDDEN");
    }

    @Test
    void userSoftDeleteRestoreRejectsAStaleEtagAndPersistsAuditEvents() throws Exception {
        AuthenticatedSession session = authenticatedSession("lifecycle");
        String email = "lifecycle-" + UUID.randomUUID() + "@example.test";
        MvcResult created = mvc.perform(post("/api/v1/users")
                        .cookie(session.accessCookie(), session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String creationEtag = created.getResponse().getHeader(HttpHeaders.ETAG);

        MvcResult deleted = mvc.perform(delete("/api/v1/users/{userId}", userId)
                        .cookie(session.accessCookie(), session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken())
                        .header(HttpHeaders.IF_MATCH, creationEtag))
                .andExpect(status().isNoContent())
                .andReturn();
        String deletionEtag = deleted.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(deletionEtag).isNotEqualTo(creationEtag);

        MvcResult archived = mvc.perform(get("/api/v1/users/{userId}", userId).cookie(session.accessCookie()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(archived.getResponse().getContentAsString()).get("deleted").asBoolean()).isTrue();

        MvcResult staleRestore = mvc.perform(post("/api/v1/users/{userId}/restore", userId)
                        .cookie(session.accessCookie(), session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken())
                        .header(HttpHeaders.IF_MATCH, creationEtag))
                .andExpect(status().isPreconditionFailed())
                .andReturn();
        assertThat(errorCode(staleRestore)).isEqualTo("STALE_VERSION");

        mvc.perform(post("/api/v1/users/{userId}/restore", userId)
                        .cookie(session.accessCookie(), session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken())
                        .header(HttpHeaders.IF_MATCH, deletionEtag))
                .andExpect(status().isNoContent());

        MvcResult restored = mvc.perform(get("/api/v1/users/{userId}", userId).cookie(session.accessCookie()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(restored.getResponse().getContentAsString()).get("deleted").asBoolean()).isFalse();

        Integer events = jdbc.queryForObject("""
                select count(*) from audit_events
                where target_type = 'USER_PROFILE' and target_id = ?
                  and event_type in ('USER_CREATED', 'USER_DELETED', 'USER_RESTORED')
                """, Integer.class, UUID.fromString(userId));
        assertThat(events).isEqualTo(3);
    }

    @Test
    void refreshRotationDetectsReuseRevokesTheFamilyAndWritesAnAuditEvent() throws Exception {
        AuthenticatedSession session = authenticatedSession("refresh");

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(session.refreshCookie(), session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRefresh = rotated.getResponse().getCookie("PD_REFRESH");
        assertThat(rotatedRefresh).isNotNull();

        MvcResult reuse = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(session.refreshCookie(), session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(errorCode(reuse)).isEqualTo("UNAUTHENTICATED");

        Integer activeSessions = jdbc.queryForObject(
                "select count(*) from refresh_sessions where admin_id = ? and revoked_at is null",
                Integer.class, session.adminId());
        Integer reuseMarkers = jdbc.queryForObject(
                "select count(*) from refresh_sessions where admin_id = ? and reuse_detected_at is not null",
                Integer.class, session.adminId());
        Integer reuseAudits = jdbc.queryForObject(
                "select count(*) from audit_events where actor_admin_id = ? and event_type = 'REFRESH_TOKEN_REUSE_DETECTED'",
                Integer.class, session.adminId());
        assertThat(activeSessions).isZero();
        assertThat(reuseMarkers).isEqualTo(1);
        assertThat(reuseAudits).isEqualTo(1);

        // The replacement token was active before reuse detection; it is now revoked with its family.
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedRefresh, session.csrfCookie())
                        .header("X-XSRF-TOKEN", session.csrfToken()))
                .andExpect(status().isUnauthorized());
    }

    private AuthenticatedSession authenticatedSession(String scenario) throws Exception {
        String email = scenario + "-admin-" + UUID.randomUUID() + "@example.test";
        AdminAccount admin = admins.saveAndFlush(new AdminAccount(email, passwordEncoder.encode("CorrectHorseBattery1"), "API Administrator"));

        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"CorrectHorseBattery1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie accessCookie = login.getResponse().getCookie("PD_ACCESS");
        Cookie refreshCookie = login.getResponse().getCookie("PD_REFRESH");
        assertThat(csrfCookie).isNotNull();
        assertThat(accessCookie).isNotNull();
        assertThat(refreshCookie).isNotNull();
        return new AuthenticatedSession(admin.getId(), accessCookie, refreshCookie, csrfCookie, csrfToken);
    }

    private String errorCode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
    }

    private String userJson(String email) {
        return "{\"email\":\"" + email + "\",\"firstName\":\"Casey\",\"lastName\":\"Jones\"}";
    }

    private record AuthenticatedSession(
            UUID adminId, Cookie accessCookie, Cookie refreshCookie, Cookie csrfCookie, String csrfToken) {
    }
}
