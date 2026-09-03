package com.profiledirectory.auth.application;

import com.profiledirectory.audit.application.AuditService;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.auth.domain.RefreshSession;
import com.profiledirectory.auth.domain.RefreshSessionRepository;
import com.profiledirectory.config.AppSecurityProperties;
import com.profiledirectory.shared.error.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RefreshSessionRepository sessions;
    private final AppSecurityProperties properties;
    private final AuditService audit;

    public RefreshTokenService(RefreshSessionRepository sessions, AppSecurityProperties properties, AuditService audit) {
        this.sessions = sessions;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public RefreshResult issue(AdminAccount admin, HttpServletRequest request) {
        Instant now = Instant.now();
        Instant absoluteExpiry = now.plus(properties.getRefreshToken().getAbsoluteTtl());
        return issue(admin, UUID.randomUUID(), absoluteExpiry, now, request);
    }

    /**
     * Reuse and expiry are security state transitions, not failed business work.  They must be
     * committed even though the caller receives a 401, otherwise a stolen rotated token could
     * leave its replacement session usable after the rejection response.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = UnauthenticatedException.class)
    public RefreshResult rotate(String rawToken, HttpServletRequest request) {
        RefreshSession current = sessions.findForRotationByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthenticatedException("Session is no longer valid"));
        Instant now = Instant.now();
        if (current.getRevokedAt() != null) {
            current.markReuseDetected(now);
            revokeFamily(current.getFamilyId(), now);
            audit.record(current.getAdmin(), "REFRESH_TOKEN_REUSE_DETECTED", "REFRESH_SESSION", current.getId(), Map.of("familyId", current.getFamilyId().toString()));
            throw new UnauthenticatedException("Session is no longer valid");
        }
        if (current.isExpired(now) || !current.getAdmin().isActive()) {
            current.revoke(now, null);
            audit.record(current.getAdmin(), "REFRESH_SESSION_EXPIRED", "REFRESH_SESSION", current.getId(), Map.of());
            throw new UnauthenticatedException("Session is no longer valid");
        }
        RefreshResult next = issue(current.getAdmin(), current.getFamilyId(), current.getAbsoluteExpiresAt(), now, request);
        current.revoke(now, next.sessionId());
        sessions.save(current);
        audit.record(current.getAdmin(), "REFRESH_TOKEN_ROTATED", "REFRESH_SESSION", next.sessionId(), Map.of("familyId", current.getFamilyId().toString()));
        return next;
    }

    @Transactional
    public void revokeIfPresent(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessions.findForRotationByTokenHash(hash(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.revoke(Instant.now(), null);
                audit.record(session.getAdmin(), "LOGOUT", "REFRESH_SESSION", session.getId(), Map.of());
            }
        });
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeExpiredSessions() {
        sessions.deleteAll(sessions.findByExpiresAtBefore(Instant.now().minus(properties.getRefreshToken().getAbsoluteTtl())));
    }

    private RefreshResult issue(AdminAccount admin, UUID familyId, Instant absoluteExpiry, Instant now, HttpServletRequest request) {
        Instant slidingExpiry = now.plus(properties.getRefreshToken().getSlidingTtl());
        Instant expiresAt = slidingExpiry.isBefore(absoluteExpiry) ? slidingExpiry : absoluteExpiry;
        String raw = randomToken();
        RefreshSession session = new RefreshSession(admin, hash(raw), familyId, expiresAt, absoluteExpiry, userAgent(request), request.getRemoteAddr());
        RefreshSession saved = sessions.saveAndFlush(session);
        return new RefreshResult(admin, raw, saved.getId(), expiresAt);
    }

    private void revokeFamily(UUID familyId, Instant now) {
        List<RefreshSession> active = sessions.findByFamilyIdAndRevokedAtIsNull(familyId);
        for (RefreshSession session : active) {
            session.revoke(now, null);
        }
        sessions.saveAll(active);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getRefreshToken().getPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }

    private String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null : value.substring(0, Math.min(value.length(), 512));
    }

    public record RefreshResult(AdminAccount admin, String rawToken, UUID sessionId, Instant expiresAt) {
        @Override
        public String toString() {
            return "RefreshResult[redacted]";
        }
    }
}
