package dev.mytechprofile.aigateway.gateway.tools;

import tools.jackson.databind.JsonNode;

/**
 * A tool the gateway runs only after authorization. Implementations are Spring beans, found by {@link #name()}.
 *
 * <p>Adding a tool means: implement this interface, register it as a bean, add its scope to {@code ToolScopes},
 * its schema to {@code ToolCatalog}, its argument check to {@code ToolAuthorizer}, and a {@code tool_policy} row.
 * {@snippet :
 * class GetCandidateSummaryTool implements GovernedTool {
 *     public String name() {
 *         return "get_candidate_summary";
 *     }
 *
 *     public String execute(JsonNode arguments, ToolExecutionContext context) {
 *         var candidate = candidates.findByIdAndTenantId(arguments.path("candidateId").asString(), context.tenantId())
 *                 .orElseThrow();
 *         return json.createObjectNode().put("status", candidate.getStatus().name()).toString();
 *     }
 * }
 * }
 */
public interface GovernedTool {
    /** Tool name the model uses; must match {@code ToolCatalog}, {@code ToolScopes}, and {@code tool_policy}. */
    String name();

    /**
     * Runs an already authorized call and returns a JSON result for the model. Arguments are already validated.
     * The result is audited only as an HMAC, but it is sent to the model, so it must not contain PII.
     */
    String execute(JsonNode arguments, ToolExecutionContext context);
}
