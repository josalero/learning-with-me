package dev.mytechprofile.aigateway.gateway.delegation;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.mytechprofile.aigateway.shared.security.GatewayPrincipal;

/**
 * Resolves the agent the caller asked for and the active delegation grant, if any.
 *
 * {@snippet :
 * if (!agents.agentExists(request.agentId())) {
 *     throw new GatewayException(HttpStatus.NOT_FOUND, "Agent was not found");
 * }
 * DelegationGrantEntity grant = agents.activeGrant(principal, request.agentId())
 *         .orElseThrow(() -> new GatewayException(HttpStatus.FORBIDDEN, "No active delegation for this agent"));
 * }
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

    /** Whether the agent id is registered. Checked once, when the execution is accepted. */
    public boolean agentExists(String agentId) {
        return agents.existsById(agentId);
    }

    /** The unexpired grant from this principal to this agent in the principal's tenant, if any. */
    public Optional<DelegationGrantEntity> activeGrant(GatewayPrincipal principal, String agentId) {
        return grants.findActive(principal.tenantId(), principal.id(), agentId, clock.instant());
    }
}
