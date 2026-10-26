package dev.mytechprofile.statefulagent.session;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSessionRepository extends JpaRepository<AgentSessionEntity, String> {
}
