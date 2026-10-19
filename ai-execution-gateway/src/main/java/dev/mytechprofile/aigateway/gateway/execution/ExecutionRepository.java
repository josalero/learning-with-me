package dev.mytechprofile.aigateway.gateway.execution;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Executions. API reads always go through {@code findByIdAndTenantId} so other tenants' rows stay invisible.
 */
public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    /** Empty for another tenant's execution, which the API answers as {@code 404}. */
    Optional<ExecutionEntity> findByIdAndTenantId(UUID id, String tenantId);
}
