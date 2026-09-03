package com.profiledirectory.auth.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session join fetch session.admin where session.tokenHash = :tokenHash")
    Optional<RefreshSession> findForRotationByTokenHash(@Param("tokenHash") String tokenHash);

    List<RefreshSession> findByFamilyIdAndRevokedAtIsNull(UUID familyId);
    List<RefreshSession> findByExpiresAtBefore(Instant cutoff);
}
