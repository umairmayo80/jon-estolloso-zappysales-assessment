package com.profiledirectory.auth.application;

import com.profiledirectory.audit.application.AuditService;
import com.profiledirectory.auth.api.AdminResponse;
import com.profiledirectory.auth.api.LoginRequest;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.AdminAccountRepository;
import com.profiledirectory.shared.error.InvalidCredentialsException;
import com.profiledirectory.shared.error.UnauthenticatedException;
import com.profiledirectory.shared.web.InputNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AdminAccountRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;

    public AuthService(
            AdminAccountRepository admins,
            PasswordEncoder passwordEncoder,
            JwtService jwt,
            RefreshTokenService refreshTokens,
            AuditService audit) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Transactional
    public AuthSession login(LoginRequest request, HttpServletRequest servletRequest) {
        AdminAccount account = admins.findByEmail(InputNormalizer.email(request.email())).orElse(null);
        if (account == null || !account.isActive() || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            audit.recordSecurityEvent(
                    account,
                    "LOGIN_FAILED",
                    account == null ? "AUTHENTICATION" : "ADMIN_ACCOUNT",
                    account == null ? null : account.getId(),
                    Map.of("outcome", "invalid_credentials"));
            throw new InvalidCredentialsException();
        }
        RefreshTokenService.RefreshResult refresh = refreshTokens.issue(account, servletRequest);
        audit.record(account, "LOGIN_SUCCEEDED", "ADMIN_ACCOUNT", account.getId(), Map.of());
        return new AuthSession(AdminResponse.from(account), jwt.issue(account), refresh.rawToken(), ttlUntil(refresh.expiresAt()));
    }

    @Transactional
    public AuthSession refresh(String rawRefreshToken, HttpServletRequest servletRequest) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthenticatedException("Session is no longer valid");
        }
        RefreshTokenService.RefreshResult refresh = refreshTokens.rotate(rawRefreshToken, servletRequest);
        return new AuthSession(AdminResponse.from(refresh.admin()), jwt.issue(refresh.admin()), refresh.rawToken(), ttlUntil(refresh.expiresAt()));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revokeIfPresent(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public AdminResponse me(AdminPrincipal principal) {
        AdminAccount account = admins.findById(principal.id()).orElseThrow(UnauthenticatedException::new);
        if (!account.isActive()) {
            throw new UnauthenticatedException();
        }
        return AdminResponse.from(account);
    }

    private Duration ttlUntil(Instant instant) {
        return Duration.between(Instant.now(), instant);
    }

    public record AuthSession(AdminResponse admin, String accessToken, String refreshToken, Duration refreshTtl) {
        @Override
        public String toString() {
            return "AuthSession[redacted]";
        }
    }
}
