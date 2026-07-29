package dev.mytechprofile.gateway.examples.orchestrator.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import tools.jackson.databind.JsonNode;

import dev.mytechprofile.gateway.examples.orchestrator.mcp.McpToolGateway;
import dev.mytechprofile.gateway.examples.orchestrator.mcp.McpToolGateway.ToolSummary;

/**
 * REST façade that orchestrates via MCP tools (no Feign to recruiting/SQL/protected APIs).
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class OrchestratorController {

    private final McpToolGateway mcpTools;

    public OrchestratorController(McpToolGateway mcpTools) {
        this.mcpTools = mcpTools;
    }

    @GetMapping("/tools")
    public List<ToolSummary> listTools() {
        return mcpTools.listTools();
    }

    /**
     * Generic tool call for exploration. Prefer domain endpoints for product APIs.
     */
    @PostMapping("/tools/{toolName}")
    public JsonNode callTool(
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> arguments,
            @RequestHeader(value = "X-Approve", defaultValue = "true") boolean approve) {
        return mcpTools.callTool(toolName, arguments, approve);
    }

    @GetMapping("/candidates/search")
    public JsonNode searchCandidates(
            @RequestParam @NotBlank String skill,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) @Min(0) Integer minimumExperience,
            @RequestParam(required = false) @Min(1) @Max(25) Integer limit) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("skill", skill);
        if (location != null) {
            args.put("location", location);
        }
        if (minimumExperience != null) {
            args.put("minimumExperience", minimumExperience);
        }
        if (limit != null) {
            args.put("limit", limit);
        }
        return mcpTools.callTool("search_candidates", args, true);
    }

    @PostMapping("/candidates/{candidateReference}/stage")
    public JsonNode advanceStage(
            @PathVariable String candidateReference,
            @RequestBody AdvanceStageRequest request,
            @RequestHeader(value = "X-Approve", defaultValue = "true") boolean approve) {
        return mcpTools.callTool(
                "advance_candidate_stage",
                Map.of(
                        "candidateReference", candidateReference,
                        "targetStage", request.targetStage()),
                approve);
    }

    @GetMapping("/inventory/low-stock")
    public JsonNode lowStock(
            @RequestParam @NotBlank String warehouse,
            @RequestParam(defaultValue = "10") @Min(0) @Max(1000) int threshold) {
        return mcpTools.callTool(
                "find_low_stock_products",
                Map.of("warehouse", warehouse, "threshold", threshold),
                true);
    }

    @GetMapping("/secure/ping")
    public JsonNode securePing() {
        return mcpTools.callTool("secure_ping", Map.of(), true);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ProblemDetail> notFound(NoSuchElementException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    public record AdvanceStageRequest(@NotBlank String targetStage) {}
}
