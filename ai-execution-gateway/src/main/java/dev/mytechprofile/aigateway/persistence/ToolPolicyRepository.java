package dev.mytechprofile.aigateway.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolPolicyRepository extends JpaRepository<ToolPolicyEntity, ToolPolicyEntity.Key> {
    boolean existsByAgentIdAndToolName(String agentId, String toolName);
}
