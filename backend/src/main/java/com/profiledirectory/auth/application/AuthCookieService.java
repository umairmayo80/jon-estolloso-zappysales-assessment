package com.profiledirectory.auth.application;

import com.profiledirectory.config.AppSecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {
    public static final String ACCESS_COOKIE = "PD_ACCESS";
    public static final String REFRESH_COOKIE = "PD_REFRESH";
    private final AppSecurityProperties properties;

    public AuthCookieService(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public void writeSession(HttpServletResponse response, String accessToken, String refreshToken, Duration refreshTtl) {
        add(response, ACCESS_COOKIE, accessToken, "/api/v1", properties.getJwt().getAccessTtl());
        add(response, REFRESH_COOKIE, refreshToken, "/api/v1/auth", refreshTtl.isNegative() ? Duration.ZERO : refreshTtl);
    }

    public void clearSession(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", "/api/v1", Duration.ZERO);
        add(response, REFRESH_COOKIE, "", "/api/v1/auth", Duration.ZERO);
    }

    public void clearAccess(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", "/api/v1", Duration.ZERO);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.getCookie().isSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
