package dev.mytechprofile.aigateway.recruiting.tools;

import java.util.ArrayList;
import java.util.List;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionEntity;
import dev.mytechprofile.aigateway.gateway.execution.ExecutionRepository;
import dev.mytechprofile.aigateway.gateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import dev.mytechprofile.aigateway.recruiting.candidate.EvidenceRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code search_evidence}: returns the candidate's evidence snippets and remembers their ids on the execution,
 * so the evaluator can tell a real citation from an invented one.
 */
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
