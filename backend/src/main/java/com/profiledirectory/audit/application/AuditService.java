package com.profiledirectory.audit.application;

import com.profiledirectory.audit.domain.AuditEvent;
import com.profiledirectory.audit.domain.AuditEventRepository;
import com.profiledirectory.auth.domain.AdminAccount;
import com.profiledirectory.shared.web.RequestContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Metadata callers supply must be non-sensitive; passwords and raw tokens are never audited. */
    public void record(AdminAccount actor, String eventType, String targetType, UUID targetId, Map<String, ?> metadata) {
        repository.save(new AuditEvent(actor, eventType, targetType, targetId, RequestContext.requestId(), asJson(metadata)));
    }

    /**
     * Security-relevant failures must survive the rejected caller transaction. The actor is
     * intentionally nullable so an unknown-account login attempt can be observed without
     * recording the submitted email address or password.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(
            AdminAccount actor, String eventType, String targetType, UUID targetId, Map<String, ?> metadata) {
        repository.saveAndFlush(new AuditEvent(actor, eventType, targetType, targetId, RequestContext.requestId(), asJson(metadata)));
    }

    private String asJson(Map<String, ?> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JacksonException ignored) {
            return "{}";
        }
    }
}
