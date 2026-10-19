package dev.mytechprofile.aigateway.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DelegationGrantRepository extends JpaRepository<DelegationGrantEntity, UUID> {

    @Query("""
            select g from DelegationGrantEntity g
            where g.tenantId = :tenantId
              and g.principalId = :principalId
              and g.agentId = :agentId
              and (g.expiresAt is null or g.expiresAt > :now)
            """)
    Optional<DelegationGrantEntity> findActive(String tenantId, String principalId, String agentId, Instant now);
}
