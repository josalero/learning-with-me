package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import dev.mytechprofile.sdlc.orchestration.RunService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Feature-run HTTP API: start, poll, SSE timeline, artifacts, and human approval.
 *
 * <p><strong>When to use:</strong> dashboard Feature and Results tabs.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * POST /api/v1/runs {"teamId":"default-scrum-team","projectId":"users-service-java","featureRequest":"..."}
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

    private final RunService runs;

    /**
     * Creates the run API.
     *
     * @param runs run facade
     */
    public RunController(RunService runs) {
        this.runs = runs;
    }

    /**
     * Starts a feature run asynchronously.
     *
     * @param request team, project, feature text
     * @return pending run
     */
    @PostMapping
    public ResponseEntity<RunOutcome> start(@Valid @RequestBody RunRequest request) {
        RunOutcome outcome =
                runs.start(new RunCommand(request.teamId(), request.projectId(), request.featureRequest()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome);
    }

    /**
     * Lists runs newest first.
     *
     * @return outcomes
     */
    @GetMapping
    public List<RunOutcome> list() {
        return runs.list();
    }

    /**
     * Returns one run.
     *
     * @param id run id
     * @return outcome
     */
    @GetMapping("/{id}")
    public RunOutcome get(@PathVariable String id) {
        return runs.get(id);
    }

    /**
     * Streams step events for a run.
     *
     * @param id run id
     * @return SSE emitter
     */
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(2 * 60 * 60 * 1000L);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<AutoCloseable> subscription = new AtomicReference<>(() -> {});
        Runnable release = () -> {
            closed.set(true);
            closeQuietly(subscription.get());
        };
        emitter.onCompletion(release);
        emitter.onTimeout(release);
        emitter.onError(error -> release.run());
        subscription.set(runs.subscribe(id, event -> {
            if (closed.get()) {
                closeQuietly(subscription.get());
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("step").data(event));
            } catch (IOException | IllegalStateException ex) {
                // Browser reconnected or navigated away; drop the stream instead of failing the run.
                closed.set(true);
                emitter.complete();
                closeQuietly(subscription.get());
            }
        }));
        return emitter;
    }

    /**
     * Lists artifact file names.
     *
     * @param id run id
     * @return file names
     */
    @GetMapping("/{id}/artifacts")
    public List<String> artifacts(@PathVariable String id) {
        return runs.artifacts(id);
    }

    /**
     * Reads one artifact as text.
     *
     * @param id run id
     * @param fileName plain file name
     * @return file body
     */
    @GetMapping("/{id}/artifacts/{fileName}")
    public ResponseEntity<String> artifact(@PathVariable String id, @PathVariable String fileName) {
        String body = runs.artifact(id, fileName);
        MediaType type = fileName.endsWith(".json") ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok().contentType(type).body(body);
    }

    /**
     * Completes a human stakeholder wait.
     *
     * @param id run id
     * @param request decision body
     * @return updated outcome
     */
    @PostMapping("/{id}/approve")
    public RunOutcome approve(@PathVariable String id, @Valid @RequestBody ApprovalRequest request) {
        List<String> reasons = request.reasons() == null ? List.of() : request.reasons();
        List<String> followUps = request.followUps() == null ? List.of() : request.followUps();
        return runs.approve(id, new StakeholderDecision(request.decision(), reasons, followUps));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // subscription already gone
        }
    }
}
