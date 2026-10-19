package dev.mytechprofile.aigateway.authority;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.mytechprofile.aigateway.persistence.AgentRepository;
import dev.mytechprofile.aigateway.persistence.DelegationGrantEntity;
import dev.mytechprofile.aigateway.persistence.DelegationGrantRepository;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;

/**
 * Resolves the agent the caller asked for and the active delegation grant, if any.
 */
@Service
public class AgentResolver {

    private final AgentRepository agents;
    private final DelegationGrantRepository grants;
    private final Clock clock;

    public AgentResolver(AgentRepository agents, DelegationGrantRepository grants, Clock clock) {
        this.agents = agents;
        this.grants = grants;
        this.clock = clock;
    }

    public boolean agentExists(String agentId) {
        return agents.existsById(agentId);
    }

    public Optional<DelegationGrantEntity> activeGrant(GatewayPrincipal principal, String agentId) {
        return grants.findActive(principal.tenantId(), principal.id(), agentId, clock.instant());
    }
}
