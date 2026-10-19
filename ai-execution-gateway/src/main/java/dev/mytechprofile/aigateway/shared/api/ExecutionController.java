package dev.mytechprofile.aigateway.shared.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionQueryService;
import dev.mytechprofile.aigateway.gateway.execution.ExecutionService;
import dev.mytechprofile.aigateway.shared.security.CurrentPrincipal;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.validation.Valid;

/**
 * HTTP entry of the flow. Every request needs {@code X-API-Key}; the caller's tenant always comes from the key.
 *
 * {@snippet lang=shell :
 * # start: 202 with Location: /api/v1/executions/{id}
 * curl -s -X POST localhost:8080/api/v1/executions \
 *   -H "X-API-Key: $DEMO_RECRUITER_API_KEY" -H 'Content-Type: application/json' \
 *   -d '{"agentId":"screening-agent","taskType":"CANDIDATE_EVALUATION","candidateId":"C-101","requisitionId":"R-7"}'
 *
 * # poll until COMPLETED, BUDGET_EXHAUSTED, or FAILED
 * curl -s localhost:8080/api/v1/executions/$ID -H "X-API-Key: $DEMO_RECRUITER_API_KEY"
 *
 * # read back who acted and every step
 * curl -s localhost:8080/api/v1/executions/$ID/reconstruction -H "X-API-Key: $DEMO_RECRUITER_API_KEY"
 * }
 */
@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ExecutionService executions;
    private final ExecutionQueryService queries;
    private final Tracer tracer;

    public ExecutionController(ExecutionService executions, ExecutionQueryService queries, Tracer tracer) {
        this.executions = executions;
        this.queries = queries;
        this.tracer = tracer;
    }

    /** Starts an execution: {@code 202} with a {@code Location} to poll. See {@code ExecutionService#accept}. */
    @PostMapping
    public ResponseEntity<AcceptedExecution> create(@Valid @RequestBody CreateExecutionRequest request) {
        var span = tracer.spanBuilder("ai.execution.accept").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            var saved = executions.accept(
                    CurrentPrincipal.get(), request, span.getSpanContext().getTraceId(), Context.current());
            AcceptedExecution body = new AcceptedExecution(
                    saved.getId(), saved.getTraceId(), saved.getStatus());
            return ResponseEntity.accepted()
                    .location(URI.create("/api/v1/executions/" + saved.getId()))
                    .body(body);
        } finally {
            span.end();
        }
    }

    /** Current status; poll until it is terminal. {@code 404} for another tenant's execution. */
    @GetMapping("/{id}")
    public ExecutionView get(@PathVariable UUID id) {
        return queries.get(CurrentPrincipal.get(), id);
    }

    /** Who acted, every step with its decision and HMACs, and the totals. No prompts or candidate data. */
    @GetMapping("/{id}/reconstruction")
    public ReconstructionView reconstruction(@PathVariable UUID id) {
        return queries.reconstruct(CurrentPrincipal.get(), id);
    }

    /** Transition requests the agent created, so a user can approve one. */
    @GetMapping("/{id}/transition-requests")
    public List<TransitionRequestView> transitionRequests(@PathVariable UUID id) {
        return queries.transitionRequests(CurrentPrincipal.get(), id);
    }
}
