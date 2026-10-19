package dev.mytechprofile.aigateway.gateway.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Steps of one execution, in the order they happened.
 */
public interface ExecutionStepRepository extends JpaRepository<ExecutionStepEntity, UUID> {
    /** Steps in {@code seq} order, as they happened. */
    List<ExecutionStepEntity> findByExecutionIdOrderBySeqAsc(UUID executionId);
}
