package dev.mytechprofile.aigateway.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    Optional<ExecutionEntity> findByIdAndTenantId(UUID id, String tenantId);
}
