package dev.mytechprofile.aigateway.gateway.delegation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Row of {@code delegation_grant}: principal P lets agent A act for it in tenant T, within these limits.
 *
 * <p>Every limit is {@code NOT NULL}, so there is no unbounded grant. No grant means the request is {@code 403}.
 */
@Entity
@Table(name = "delegation_grant")
public class DelegationGrantEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "principal_id", nullable = false)
    private String principalId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "max_tool_calls", nullable = false)
    private int maxToolCalls;

    @Column(name = "max_model_calls", nullable = false)
    private int maxModelCalls;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens;

    @Column(name = "max_cost_micros", nullable = false)
    private long maxCostMicros;

    @Column(name = "expires_at", columnDefinition = "timestamptz")
    private Instant expiresAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public long getMaxCostMicros() {
        return maxCostMicros;
    }

    public void setMaxCostMicros(long maxCostMicros) {
        this.maxCostMicros = maxCostMicros;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
