package dev.mytechprofile.langgraph4j.cursor;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;

/**
 * Launches a Cursor Cloud Agent and polls the run until it finishes.
 */
public final class CloudCursorImplementer implements ImplementationPort {

    private static final Logger log = LoggerFactory.getLogger(CloudCursorImplementer.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final String model;
    private final Duration pollInterval;
    private final Duration pollTimeout;
    private final boolean autoCreatePr;

    public CloudCursorImplementer(
            RestClient restClient,
            String model,
            Duration pollInterval,
            Duration pollTimeout,
            boolean autoCreatePr) {
        this.restClient = restClient;
        this.model = model;
        this.pollInterval = pollInterval;
        this.pollTimeout = pollTimeout;
        this.autoCreatePr = autoCreatePr;
    }

    @Override
    public ImplementationResult implement(ImplementationRequest request) {
        if (request.repoUrl() == null || request.repoUrl().isBlank()) {
            return new ImplementationResult(
                    "",
                    "",
                    "",
                    "Cloud implementer needs a GitHub repo URL",
                    false,
                    "Set repoUrl on the task or CURSOR_REPO_URL",
                    "CLOUD");
        }
        try {
            Map<String, Object> created = createAgent(request);
            String agentId = nestedText(created, "agent", "id");
            String runId = nestedText(created, "run", "id");
            String agentUrl = nestedText(created, "agent", "url");
            log.info("Started Cursor cloud agent (id length={})", agentId.length());
            Map<String, Object> run = waitForRun(agentId, runId);
            String status = text(run, "status");
            boolean passed = "FINISHED".equals(status);
            String summary = text(run, "result");
            if (summary.isBlank()) {
                summary = "Cursor run ended with status " + status;
            }
            return new ImplementationResult(agentId, runId, agentUrl, summary, passed, status, "CLOUD");
        } catch (RestClientResponseException ex) {
            return new ImplementationResult(
                    "",
                    "",
                    "",
                    "Cursor API rejected the request",
                    false,
                    "HTTP " + ex.getStatusCode().value(),
                    "CLOUD");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ImplementationResult("", "", "", "Interrupted while waiting for Cursor", false, "", "CLOUD");
        }
    }

    private Map<String, Object> createAgent(ImplementationRequest request) {
        Map<String, Object> repo = new LinkedHashMap<>();
        repo.put("url", request.repoUrl());
        repo.put("startingRef", request.startingRef() == null || request.startingRef().isBlank()
                ? "main"
                : request.startingRef());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", Map.of("text", request.prompt()));
        if (model != null && !model.isBlank()) {
            body.put("model", Map.of("id", model));
        }
        body.put("repos", List.of(repo));
        body.put("autoCreatePR", autoCreatePr);
        body.put("name", "langgraph4j-poc");

        Map<String, Object> response = restClient.post()
                .uri("/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP);
        return response == null ? Map.of() : response;
    }

    private Map<String, Object> waitForRun(String agentId, String runId) throws InterruptedException {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        Map<String, Object> latest = Map.of();
        while (System.nanoTime() < deadline) {
            Map<String, Object> body = restClient.get()
                    .uri("/v1/agents/{agentId}/runs/{runId}", agentId, runId)
                    .retrieve()
                    .body(MAP);
            latest = body == null ? Map.of() : body;
            if (isTerminal(text(latest, "status"))) {
                return latest;
            }
            Thread.sleep(pollInterval.toMillis());
        }
        return latest;
    }

    private static boolean isTerminal(String status) {
        return "FINISHED".equals(status)
                || "ERROR".equals(status)
                || "CANCELLED".equals(status)
                || "EXPIRED".equals(status);
    }

    @SuppressWarnings("unchecked")
    private static String nestedText(Map<String, Object> root, String parent, String child) {
        Object node = root.get(parent);
        if (node instanceof Map<?, ?> map) {
            return text((Map<String, Object>) map, child);
        }
        return "";
    }

    private static String text(Map<String, Object> node, String key) {
        if (node == null) {
            return "";
        }
        Object value = node.get(key);
        return value == null ? "" : value.toString();
    }
}
