package dev.mytechprofile.aigateway.recruiting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.mytechprofile.aigateway.authority.ToolAuthorizer;
import dev.mytechprofile.aigateway.domain.CandidateStatus;
import dev.mytechprofile.aigateway.domain.Recommendation;
import dev.mytechprofile.aigateway.domain.TransitionRequestStatus;
import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.persistence.EvidenceRepository;
import dev.mytechprofile.aigateway.persistence.ExecutionEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionRepository;
import dev.mytechprofile.aigateway.persistence.RecommendationDraftEntity;
import dev.mytechprofile.aigateway.persistence.RecommendationDraftRepository;
import dev.mytechprofile.aigateway.persistence.TransitionRequestEntity;
import dev.mytechprofile.aigateway.persistence.TransitionRequestRepository;
import dev.mytechprofile.aigateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.tools.ToolExecutionContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

class GetCandidateSummaryTool implements GovernedTool {

    private final CandidateRepository candidates;
    private final JsonMapper json;

    GetCandidateSummaryTool(CandidateRepository candidates, JsonMapper json) {
        this.candidates = candidates;
        this.json = json;
    }

    @Override
    public String name() {
        return "get_candidate_summary";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        var candidate = candidates
                .findByIdAndTenantId(arguments.path("candidateId").asString(), context.tenantId())
                .orElseThrow();
        ObjectNode body = json.createObjectNode();
        body.put("candidateId", candidate.getId());
        body.put("status", candidate.getStatus().name());
        body.put("requisitionId", candidate.getRequisitionId());
        body.put("yearsExperience", candidate.getYearsExperience());
        ArrayNode skills = body.putArray("skills");
        candidate.getSkills().forEach(skills::add);
        return body.toString();
    }
}

class SearchEvidenceTool implements GovernedTool {

    private final EvidenceRepository evidence;
    private final ExecutionRepository executions;
    private final JsonMapper json;

    SearchEvidenceTool(EvidenceRepository evidence, ExecutionRepository executions, JsonMapper json) {
        this.evidence = evidence;
        this.executions = executions;
        this.json = json;
    }

    @Override
    public String name() {
        return "search_evidence";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        String candidateId = arguments.path("candidateId").asString();
        var rows = evidence.findByCandidateIdAndTenantId(candidateId, context.tenantId());
        ExecutionEntity execution = executions.findById(context.executionId()).orElseThrow();
        List<String> retrieved = new ArrayList<>(
                execution.getRetrievedEvidenceIds() == null ? List.of() : execution.getRetrievedEvidenceIds());
        ObjectNode body = json.createObjectNode();
        ArrayNode items = body.putArray("items");
        for (var row : rows) {
            if (!retrieved.contains(row.getId())) {
                retrieved.add(row.getId());
            }
            ObjectNode item = items.addObject();
            item.put("evidenceId", row.getId());
            item.put("snippet", row.getSnippet());
        }
        execution.setRetrievedEvidenceIds(retrieved);
        executions.save(execution);
        return body.toString();
    }
}

class DraftRecommendationTool implements GovernedTool {

    private final RecommendationDraftRepository drafts;
    private final JsonMapper json;

    DraftRecommendationTool(RecommendationDraftRepository drafts, JsonMapper json) {
        this.drafts = drafts;
        this.json = json;
    }

    @Override
    public String name() {
        return "draft_recommendation";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        RecommendationDraftEntity draft = new RecommendationDraftEntity();
        draft.setId(UUID.randomUUID());
        draft.setExecutionId(context.executionId());
        draft.setCandidateId(arguments.path("candidateId").asString());
        draft.setRecommendation(Recommendation.valueOf(arguments.path("recommendation").asString()));
        draft.setRationale(arguments.path("rationale").asString());
        draft.setCitedEvidenceIds(ToolAuthorizer.stringList(arguments.path("citedEvidenceIds")));
        drafts.save(draft);
        return json.createObjectNode().put("draftId", draft.getId().toString()).toString();
    }
}

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

class ExecuteStatusTransitionTool implements GovernedTool {

    @Override
    public String name() {
        return "execute_status_transition";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        throw new IllegalStateException("status transitions are not executed by tools");
    }
}
