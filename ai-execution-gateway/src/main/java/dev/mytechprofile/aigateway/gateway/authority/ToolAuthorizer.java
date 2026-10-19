package dev.mytechprofile.aigateway.gateway.authority;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.mytechprofile.aigateway.gateway.delegation.DelegationGrantEntity;
import dev.mytechprofile.aigateway.gateway.delegation.ToolPolicyRepository;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateEntity;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStateMachine;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStatus;
import dev.mytechprofile.aigateway.recruiting.recommendation.Recommendation;
import dev.mytechprofile.aigateway.shared.security.GatewayPrincipal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decides whether a proposed tool call may run. Denial does not change domain state.
 *
 * <p>Checks run in this order and the first failure wins, so each denial has exactly one reason:
 * <ol>
 *   <li>{@code DELEGATION_MISSING}: no active, unexpired grant from this principal to this agent</li>
 *   <li>{@code TOOL_NOT_GRANTED}: the agent's tool policy does not list the tool</li>
 *   <li>{@code SCOPE_MISSING}: the delegating principal lacks the tool's scope</li>
 *   <li>{@code INVALID_ARGUMENTS}: arguments are not valid JSON for the tool</li>
 *   <li>{@code CANDIDATE_NOT_FOUND}: the candidate is not in the principal's tenant</li>
 *   <li>{@code INVALID_TRANSITION}: the requested status change does not match the current state</li>
 * </ol>
 */
@Service
public class ToolAuthorizer {

    private static final String REQUEST_TRANSITION = "request_status_transition";

    private final ToolPolicyRepository policies;
    private final CandidateRepository candidates;
    private final CandidateStateMachine stateMachine;
    private final JsonMapper json;
    private final Clock clock;

    public ToolAuthorizer(
            ToolPolicyRepository policies,
            CandidateRepository candidates,
            CandidateStateMachine stateMachine,
            JsonMapper json,
            Clock clock) {
        this.policies = policies;
        this.candidates = candidates;
        this.stateMachine = stateMachine;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Returns the first failing check, or {@code PERMITTED}. Never throws for bad model output; malformed
     * arguments are a denial, not an error.
     */
    public AuthorizationDecision authorize(
            DelegationGrantEntity grant, GatewayPrincipal principal, String agentId, String toolName, String argumentsJson) {
        if (!grantActive(grant, principal, agentId)) {
            return deny(ReasonCode.DELEGATION_MISSING);
        }
        if (!policies.existsByAgentIdAndToolName(agentId, toolName)) {
            return deny(ReasonCode.TOOL_NOT_GRANTED);
        }
        if (!hasRequiredScope(principal, toolName)) {
            return deny(ReasonCode.SCOPE_MISSING);
        }
        Optional<JsonNode> args = parseValidArguments(toolName, argumentsJson);
        if (args.isEmpty()) {
            return deny(ReasonCode.INVALID_ARGUMENTS);
        }
        Optional<CandidateEntity> candidate = candidateInTenant(args.get(), principal);
        if (candidate.isEmpty()) {
            return deny(ReasonCode.CANDIDATE_NOT_FOUND);
        }
        if (REQUEST_TRANSITION.equals(toolName) && !transitionAllowedNow(candidate.get(), args.get())) {
            return deny(ReasonCode.INVALID_TRANSITION);
        }
        return new AuthorizationDecision(ReasonCode.PERMITTED);
    }

    private static AuthorizationDecision deny(ReasonCode reason) {
        return new AuthorizationDecision(reason);
    }

    private static boolean hasRequiredScope(GatewayPrincipal principal, String toolName) {
        String requiredScope = ToolScopes.REQUIRED.get(toolName);
        return requiredScope != null && principal.scopes().contains(requiredScope);
    }

    private Optional<JsonNode> parseValidArguments(String toolName, String argumentsJson) {
        JsonNode args;
        try {
            args = json.readTree(argumentsJson == null ? "" : argumentsJson);
        } catch (JacksonException ex) {
            return Optional.empty();
        }
        boolean valid = args != null && args.isObject() && validArguments(toolName, args);
        return valid ? Optional.of(args) : Optional.empty();
    }

    private Optional<CandidateEntity> candidateInTenant(JsonNode args, GatewayPrincipal principal) {
        return candidates.findByIdAndTenantId(args.path("candidateId").asString(), principal.tenantId());
    }

    /** The model must name the candidate's current status as {@code from}, and the edge must be legal. */
    private boolean transitionAllowedNow(CandidateEntity candidate, JsonNode args) {
        CandidateStatus from = CandidateStatus.valueOf(args.path("from").asString());
        CandidateStatus to = CandidateStatus.valueOf(args.path("to").asString());
        return candidate.getStatus() == from && stateMachine.allows(from, to);
    }

    private boolean grantActive(DelegationGrantEntity grant, GatewayPrincipal principal, String agentId) {
        if (grant == null) {
            return false;
        }
        if (!grant.getTenantId().equals(principal.tenantId())
                || !grant.getPrincipalId().equals(principal.id())
                || !grant.getAgentId().equals(agentId)) {
            return false;
        }
        Instant expiresAt = grant.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(clock.instant());
    }

    private boolean validArguments(String toolName, JsonNode args) {
        if (!text(args, "candidateId")) {
            return false;
        }
        return switch (toolName) {
            case "get_candidate_summary" -> true;
            case "search_evidence" -> text(args, "query");
            case "draft_recommendation" -> validDraft(args);
            case REQUEST_TRANSITION -> validTransition(args);
            case "execute_status_transition" -> status(args, "to");
            default -> false;
        };
    }

    private static boolean validDraft(JsonNode args) {
        return text(args, "rationale") && knownRecommendation(args) && nonBlankStrings(args.path("citedEvidenceIds"));
    }

    private static boolean knownRecommendation(JsonNode args) {
        if (!text(args, "recommendation")) {
            return false;
        }
        try {
            Recommendation.valueOf(args.path("recommendation").asString());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean nonBlankStrings(JsonNode array) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (!item.isString() || item.asString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean validTransition(JsonNode args) {
        if (!status(args, "from") || !status(args, "to")) {
            return false;
        }
        try {
            UUID.fromString(args.path("draftId").asString());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean status(JsonNode args, String field) {
        if (!text(args, field)) {
            return false;
        }
        try {
            CandidateStatus.valueOf(args.path(field).asString());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean text(JsonNode args, String field) {
        JsonNode node = args.path(field);
        return node.isString() && !node.asString().isBlank();
    }
}
