package com.profiledirectory.auth.domain;

import com.profiledirectory.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private AdminAccount admin;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    private Instant revokedAt;
    private UUID replacedById;
    private Instant reuseDetectedAt;
    private Instant lastUsedAt;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 64)
    private String ipAddress;

    protected RefreshSession() {
    }

    public RefreshSession(
            AdminAccount admin,
            String tokenHash,
            UUID familyId,
            Instant expiresAt,
            Instant absoluteExpiresAt,
            String userAgent,
            String ipAddress) {
        this.admin = admin;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public AdminAccount getAdmin() { return admin; }
    public String getTokenHash() { return tokenHash; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getReuseDetectedAt() { return reuseDetectedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    public void revoke(Instant when, UUID nextSessionId) {
        this.revokedAt = when;
        this.replacedById = nextSessionId;
        this.lastUsedAt = when;
    }

    public void markReuseDetected(Instant when) {
        this.reuseDetectedAt = when;
    }
}
