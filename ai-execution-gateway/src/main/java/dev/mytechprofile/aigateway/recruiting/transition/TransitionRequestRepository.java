package dev.mytechprofile.aigateway.recruiting.transition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Transition requests by id within a tenant, or by the execution that created them.
 */
public interface TransitionRequestRepository extends JpaRepository<TransitionRequestEntity, UUID> {
    /** Empty for a request in another tenant. */
    Optional<TransitionRequestEntity> findByIdAndTenantId(UUID id, String tenantId);

    /** Requests created by one execution. */
    List<TransitionRequestEntity> findByExecutionId(UUID executionId);
}
