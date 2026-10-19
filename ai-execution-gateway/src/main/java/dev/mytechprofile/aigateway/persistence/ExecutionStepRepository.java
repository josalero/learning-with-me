package dev.mytechprofile.aigateway.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionStepRepository extends JpaRepository<ExecutionStepEntity, UUID> {
    List<ExecutionStepEntity> findByExecutionIdOrderBySeqAsc(UUID executionId);
}
