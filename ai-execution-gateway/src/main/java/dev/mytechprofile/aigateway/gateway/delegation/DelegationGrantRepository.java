package dev.mytechprofile.aigateway.gateway.delegation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Finds the one grant that is valid right now for a tenant, principal, and agent.
 */
public interface DelegationGrantRepository extends JpaRepository<DelegationGrantEntity, UUID> {

    /** The grant that is not expired at {@code now}; at most one per tenant, principal, and agent. */
    @Query("""
            select g from DelegationGrantEntity g
            where g.tenantId = :tenantId
              and g.principalId = :principalId
              and g.agentId = :agentId
              and (g.expiresAt is null or g.expiresAt > :now)
            """)
    Optional<DelegationGrantEntity> findActive(String tenantId, String principalId, String agentId, Instant now);
}
