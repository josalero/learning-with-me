package dev.mytechprofile.aigateway.gateway.delegation;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Agents by id.
 */
public interface AgentRepository extends JpaRepository<AgentEntity, String> {}
