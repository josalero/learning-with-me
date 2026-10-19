package dev.mytechprofile.aigateway.authority;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.mytechprofile.aigateway.domain.CandidateStatus;
import dev.mytechprofile.aigateway.domain.ReasonCode;
import dev.mytechprofile.aigateway.domain.Recommendation;
import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.persistence.DelegationGrantEntity;
import dev.mytechprofile.aigateway.persistence.ToolPolicyRepository;
import dev.mytechprofile.aigateway.recruiting.CandidateStateMachine;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decides whether a proposed tool call may run. Denial does not change domain state.
 */
@Service
public class ToolAuthorizer {

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

    public AuthorizationDecision authorize(
            DelegationGrantEntity grant, GatewayPrincipal principal, String agentId, String toolName, String argumentsJson) {
        if (!grantActive(grant, principal, agentId)) {
            return new AuthorizationDecision(ReasonCode.DELEGATION_MISSING);
        }
        if (!policies.existsByAgentIdAndToolName(agentId, toolName)) {
            return new AuthorizationDecision(ReasonCode.TOOL_NOT_GRANTED);
        }
        String requiredScope = ToolScopes.REQUIRED.get(toolName);
        if (requiredScope == null || !principal.scopes().contains(requiredScope)) {
            return new AuthorizationDecision(ReasonCode.SCOPE_MISSING);
        }
        JsonNode args;
        try {
            args = json.readTree(argumentsJson == null ? "" : argumentsJson);
        } catch (JacksonException ex) {
            return new AuthorizationDecision(ReasonCode.INVALID_ARGUMENTS);
        }
        if (args == null || !args.isObject() || !validArguments(toolName, args)) {
            return new AuthorizationDecision(ReasonCode.INVALID_ARGUMENTS);
        }
        var candidate = candidates.findByIdAndTenantId(args.path("candidateId").asString(), principal.tenantId());
        if (candidate.isEmpty()) {
            return new AuthorizationDecision(ReasonCode.CANDIDATE_NOT_FOUND);
        }
        if ("request_status_transition".equals(toolName)) {
            CandidateStatus from = CandidateStatus.valueOf(args.path("from").asString());
            CandidateStatus to = CandidateStatus.valueOf(args.path("to").asString());
            if (candidate.get().getStatus() != from || !stateMachine.allows(from, to)) {
                return new AuthorizationDecision(ReasonCode.INVALID_TRANSITION);
            }
        }
        return new AuthorizationDecision(ReasonCode.PERMITTED);
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
            case "request_status_transition" -> validTransition(args);
            case "execute_status_transition" -> status(args, "to");
            default -> false;
        };
    }

    private boolean validDraft(JsonNode args) {
        if (!text(args, "rationale") || !args.path("recommendation").isString()) {
            return false;
        }
        try {
            Recommendation.valueOf(args.path("recommendation").asString());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        JsonNode cited = args.path("citedEvidenceIds");
        if (!cited.isArray()) {
            return false;
        }
        for (JsonNode item : cited) {
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

    public static List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }
}
