package dev.mytechprofile.aigateway.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {}
