package dev.mytechprofile.aigateway.recruiting.tools;

import dev.mytechprofile.aigateway.gateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code get_candidate_summary}: status, requisition, skills, and years of experience. Never name or email.
 */
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
