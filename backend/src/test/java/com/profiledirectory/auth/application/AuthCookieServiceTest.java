package com.profiledirectory.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.profiledirectory.config.AppSecurityProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {
    @Test
    void writesHostOnlyStrictHttpOnlyCookiesWithScopedPaths() {
        AuthCookieService service = new AuthCookieService(new AppSecurityProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeSession(response, "access-token", "refresh-token", Duration.ofDays(7));

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0)).contains("PD_ACCESS=access-token", "Path=/api/v1", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
        assertThat(cookies.get(1)).contains("PD_REFRESH=refresh-token", "Path=/api/v1/auth", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
    }
}
