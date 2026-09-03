package com.profiledirectory.auth.api;

import com.profiledirectory.auth.application.AdminPrincipal;
import com.profiledirectory.auth.application.AuthCookieService;
import com.profiledirectory.auth.application.AuthService;
import com.profiledirectory.auth.application.CookieReader;
import com.profiledirectory.shared.error.UnauthenticatedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;
    private final AuthCookieService cookies;

    public AuthController(AuthService auth, AuthCookieService cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Create or retrieve the SPA CSRF token")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a local administrator and issue secure cookies")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            AuthService.AuthSession session = auth.login(request, servletRequest);
            writeCookies(servletResponse, session);
            return ResponseEntity.ok(new AuthResponse(session.admin()));
        } catch (UnauthenticatedException exception) {
            cookies.clearSession(servletResponse);
            throw exception;
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and issue a new access cookie")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthService.AuthSession session = auth.refresh(CookieReader.get(request, AuthCookieService.REFRESH_COOKIE), request);
            writeCookies(response, session);
            return ResponseEntity.ok(new AuthResponse(session.admin()));
        } catch (UnauthenticatedException exception) {
            cookies.clearSession(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh session and clear authentication cookies")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(CookieReader.get(request, AuthCookieService.REFRESH_COOKIE));
        cookies.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the current administrator")
    public AdminResponse me(@AuthenticationPrincipal AdminPrincipal principal) {
        return auth.me(principal);
    }

    private void writeCookies(HttpServletResponse response, AuthService.AuthSession session) {
        Duration refreshTtl = session.refreshTtl().isNegative() ? Duration.ZERO : session.refreshTtl();
        cookies.writeSession(response, session.accessToken(), session.refreshToken(), refreshTtl);
    }
}
