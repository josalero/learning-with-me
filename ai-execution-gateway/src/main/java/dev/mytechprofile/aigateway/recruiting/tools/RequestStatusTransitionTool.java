package dev.mytechprofile.aigateway.recruiting.tools;

import java.util.UUID;

import dev.mytechprofile.aigateway.gateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStatus;
import dev.mytechprofile.aigateway.recruiting.transition.TransitionRequestEntity;
import dev.mytechprofile.aigateway.recruiting.transition.TransitionRequestRepository;
import dev.mytechprofile.aigateway.recruiting.transition.TransitionRequestStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code request_status_transition}: creates a {@code PENDING} request. The candidate does not move until a user approves.
 */
class RequestStatusTransitionTool implements GovernedTool {

    private final TransitionRequestRepository requests;
    private final JsonMapper json;

    RequestStatusTransitionTool(TransitionRequestRepository requests, JsonMapper json) {
        this.requests = requests;
        this.json = json;
    }

    @Override
    public String name() {
        return "request_status_transition";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        TransitionRequestEntity request = new TransitionRequestEntity();
        request.setId(UUID.randomUUID());
        request.setTenantId(context.tenantId());
        request.setCandidateId(arguments.path("candidateId").asString());
        request.setFromStatus(CandidateStatus.valueOf(arguments.path("from").asString()));
        request.setToStatus(CandidateStatus.valueOf(arguments.path("to").asString()));
        request.setDraftId(UUID.fromString(arguments.path("draftId").asString()));
        request.setExecutionId(context.executionId());
        request.setStatus(TransitionRequestStatus.PENDING);
        requests.save(request);
        return json.createObjectNode()
                .put("transitionRequestId", request.getId().toString())
                .put("status", "PENDING")
                .toString();
    }
}
