package dev.mytechprofile.langgraph4j.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.langgraph4j.config.CursorProperties;
import dev.mytechprofile.langgraph4j.domain.FeatureBriefPlanner;
import dev.mytechprofile.langgraph4j.graph.TaskOrchestrator;
import jakarta.validation.Valid;

/**
 * REST entry for start, inspect, resume, and Mermaid of the implementation graph.
 *
 * <pre>{@code
 * POST /api/v1/tasks
 * { "brief": "Return ok from PingService" }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskOrchestrator orchestrator;
    private final CursorProperties properties;

    public TaskController(TaskOrchestrator orchestrator, CursorProperties properties) {
        this.orchestrator = orchestrator;
        this.properties = properties;
    }

    @GetMapping("/meta")
    public EngineMeta meta() {
        return new EngineMeta(
                "langgraph4j",
                "LangGraph4j + Cursor Agent",
                "1.8.26",
                properties.mode().name(),
                properties.model());
    }

    @GetMapping("/graph")
    public GraphDocument graph() {
        return new GraphDocument(orchestrator.mermaid());
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskOrchestrator.TaskView> start(@Valid @RequestBody StartTaskRequest request) {
        String brief = request.brief() == null || request.brief().isBlank()
                ? FeatureBriefPlanner.DEFAULT_BRIEF
                : request.brief();
        String repoUrl = request.repoUrl() == null || request.repoUrl().isBlank()
                ? nullToEmpty(properties.defaultRepoUrl())
                : request.repoUrl();
        return ResponseEntity.ok(orchestrator.start(brief, repoUrl, request.startingRef()));
    }

    @GetMapping("/tasks/{threadId}")
    public TaskOrchestrator.TaskView get(@PathVariable String threadId) {
        return orchestrator.get(threadId);
    }

    @PostMapping("/tasks/{threadId}/resume")
    public TaskOrchestrator.TaskView resume(
            @PathVariable String threadId,
            @Valid @RequestBody ResumeTaskRequest request) {
        return orchestrator.resume(threadId, request.decision(), request.promptOverride());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record EngineMeta(
            String engine,
            String framework,
            String frameworkVersion,
            String implementerMode,
            String model) {
    }

    public record GraphDocument(String mermaid) {
    }
}
