package dev.mytechprofile.aigateway.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_policy")
@IdClass(ToolPolicyEntity.Key.class)
public class ToolPolicyEntity {

    @Id
    @Column(name = "agent_id")
    private String agentId;

    @Id
    @Column(name = "tool_name")
    private String toolName;

    public static final class Key implements Serializable {
        private String agentId;
        private String toolName;

        public Key() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(agentId, key.agentId) && Objects.equals(toolName, key.toolName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentId, toolName);
        }
    }
}
