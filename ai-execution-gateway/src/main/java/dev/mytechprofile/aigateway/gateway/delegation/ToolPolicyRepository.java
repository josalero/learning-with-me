package dev.mytechprofile.aigateway.gateway.delegation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Checks whether an agent's policy lists a tool.
 */
public interface ToolPolicyRepository extends JpaRepository<ToolPolicyEntity, ToolPolicyEntity.Key> {
    /** Whether the agent may request the tool at all. */
    boolean existsByAgentIdAndToolName(String agentId, String toolName);
}
