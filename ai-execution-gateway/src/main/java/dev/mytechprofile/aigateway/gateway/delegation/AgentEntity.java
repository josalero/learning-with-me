package dev.mytechprofile.aigateway.gateway.delegation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code agent}. The seed has one agent, {@code screening-agent}.
 */
@Entity
@Table(name = "agent")
public class AgentEntity {

    @Id
    private String id;

    public String getId() {
        return id;
    }
}
