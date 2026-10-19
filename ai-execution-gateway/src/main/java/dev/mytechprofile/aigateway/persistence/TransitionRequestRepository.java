package dev.mytechprofile.aigateway.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitionRequestRepository extends JpaRepository<TransitionRequestEntity, UUID> {
    Optional<TransitionRequestEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<TransitionRequestEntity> findByExecutionId(UUID executionId);
}
