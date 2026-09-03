package com.profiledirectory.audit.domain;

import com.profiledirectory.auth.domain.AdminAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Database-triggered append-only audit record. No mutators are intentionally exposed. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_admin_id")
    private AdminAccount actor;

    @Column(nullable = false, length = 80, updatable = false)
    private String eventType;
    @Column(nullable = false, length = 80, updatable = false)
    private String targetType;
    @Column(updatable = false)
    private UUID targetId;
    @Column(nullable = false, length = 100, updatable = false)
    private String requestId;
    @Column(nullable = false, updatable = false)
    private String metadata;
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false, updatable = false)
    private Instant updatedAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            AdminAccount actor,
            String eventType,
            String targetType,
            UUID targetId,
            String requestId,
            String metadata) {
        this.actor = actor;
        this.eventType = eventType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.metadata = metadata;
        this.occurredAt = Instant.now();
    }

    @PrePersist
    void beforeInsert() {
        id = UUID.randomUUID();
        createdAt = occurredAt;
        updatedAt = occurredAt;
    }
}
