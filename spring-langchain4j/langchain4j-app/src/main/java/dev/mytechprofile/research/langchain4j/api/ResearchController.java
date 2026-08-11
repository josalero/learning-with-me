package dev.mytechprofile.research.langchain4j.api;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.mytechprofile.research.langchain4j.config.ResearchProperties;
import dev.mytechprofile.research.langchain4j.domain.ResearchReport;
import dev.mytechprofile.research.langchain4j.orchestration.ResearchOrchestrator;
import jakarta.validation.Valid;

/**
 * REST + SSE entry points for the LangChain4j research pipeline.
 *
 * <pre>{@code
 * POST /api/v1/research
 * { "topic": "Java virtual threads", "depth": 3 }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1")
public class ResearchController {

    private static final Logger log = LoggerFactory.getLogger(ResearchController.class);

    private final ResearchOrchestrator orchestrator;
    private final ResearchProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService researchSseExecutor;

    public ResearchController(
            ResearchOrchestrator orchestrator,
            ResearchProperties properties,
            ObjectMapper objectMapper,
            ExecutorService researchSseExecutor) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.researchSseExecutor = researchSseExecutor;
    }

    @GetMapping("/meta")
    public EngineMeta meta() {
        return new EngineMeta(
                properties.engine(),
                properties.framework(),
                properties.frameworkVersion(),
                properties.chatModel(),
                properties.researchModel()
        );
    }

    @PostMapping("/research")
    public ResponseEntity<ResearchReport> research(@Valid @RequestBody ResearchRequest request) {
        return ResponseEntity.ok(orchestrator.run(request.toCommand()));
    }

    @GetMapping(path = "/research/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String topic,
            @RequestParam(required = false) Integer depth) {
        ResearchRequest request = new ResearchRequest(topic, depth);
        SseEmitter emitter = new SseEmitter(properties.sseTimeoutMs());
        researchSseExecutor.execute(() -> {
            try {
                ResearchReport report = orchestrator.run(
                        request.toCommand(),
                        event -> send(emitter, "step", event));
                send(emitter, "report", report);
                emitter.complete();
            }
            catch (Exception ex) {
                log.error("SSE research stream failed", ex);
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to send SSE event: " + name, ex);
        }
    }
}
