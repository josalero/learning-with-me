package dev.mytechprofile.aigateway.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.aigateway.execution.ExecutionQueryService;
import dev.mytechprofile.aigateway.execution.ExecutionService;
import dev.mytechprofile.aigateway.security.CurrentPrincipal;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

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

    @GetMapping("/{id}")
    public ExecutionView get(@PathVariable UUID id) {
        return queries.get(CurrentPrincipal.get(), id);
    }

    @GetMapping("/{id}/reconstruction")
    public ReconstructionView reconstruction(@PathVariable UUID id) {
        return queries.reconstruct(CurrentPrincipal.get(), id);
    }

    @GetMapping("/{id}/transition-requests")
    public List<TransitionRequestView> transitionRequests(@PathVariable UUID id) {
        return queries.transitionRequests(CurrentPrincipal.get(), id);
    }
}
