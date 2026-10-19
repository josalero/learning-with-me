package dev.mytechprofile.aigateway.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent")
public class AgentEntity {

    @Id
    private String id;

    public String getId() {
        return id;
    }
}
