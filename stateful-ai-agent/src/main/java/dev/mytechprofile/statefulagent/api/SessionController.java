package dev.mytechprofile.statefulagent.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import dev.mytechprofile.statefulagent.session.AgentSessionService;
import dev.mytechprofile.statefulagent.session.CompatibilityMode;
import dev.mytechprofile.statefulagent.session.ModelProvider;

/**
 * Opens and reads the caller's hiring session.
 *
 * <p><strong>When to use:</strong> before any message. Store {@code id} from the
 * create response. Later message and decision calls use that value.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * POST /api/v1/sessions
 * Authorization: Basic alice:&lt;password&gt;
 *
 * { "provider": "OPENROUTER" }
 *
 * // 201
 * { "id": "2f1c9b0e-1111-4222-8333-444455556666", "provider": "OPENROUTER", "compatibilityMode": "REJECT" }
 * }</pre>
 *
 * <p>GET and PATCH are owner-only. Bob, Carol, and Dave receive 404 for Alice's
 * session. A path that is not a UUID receives 400.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final AgentSessionService sessions;
    private final AgentProperties properties;

    public SessionController(AgentSessionService sessions, AgentProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    /**
     * Creates a session for the authenticated user and the chosen provider.
     *
     * <p>Omit {@code compatibilityMode} to store {@code REJECT}. A live OpenRouter
     * run sends {@code {"provider":"OPENROUTER"}} and expects 201 with {@code id}.
     *
     * @param request provider, and an optional compatibility mode
     * @return the new session id and stored provider
     */
    @PostMapping
    ResponseEntity<SessionView> create(@Valid @RequestBody CreateSessionRequest request) {
        AgentSessionEntity created = this.sessions.create(principal(), request.provider(),
                request.compatibilityMode() == null ? CompatibilityMode.REJECT : request.compatibilityMode());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionView.from(created));
    }

    /**
     * Returns the session when the caller owns it.
     *
     * <p>Bob calling {@code GET /api/v1/sessions/{aliceSessionId}} receives 404.
     *
     * @param sessionId value of {@code id} from create
     * @return provider and compatibility mode
     */
    @GetMapping("/{sessionId}")
    SessionView get(@PathVariable UUID sessionId) {
        return SessionView.from(this.sessions.requireOwned(sessionId.toString(), principal()));
    }

    /**
     * Switches the provider used by later turns on this session.
     *
     * <p>Patching a session that stored a foreign part to {@code STUB} with
     * {@code REJECT} makes the next message 422. {@code STRIP_PROVIDER_PAYLOAD}
     * lets that turn continue.
     *
     * @param sessionId owned session
     * @param request new provider and optional compatibility mode
     * @return the updated view
     */
    @PatchMapping("/{sessionId}")
    SessionView patch(@PathVariable UUID sessionId, @Valid @RequestBody PatchSessionRequest request) {
        AgentSessionEntity owned = this.sessions.requireOwned(sessionId.toString(), principal());
        return SessionView.from(this.sessions.updateProvider(owned, request.provider(), request.compatibilityMode()));
    }

    private AgentPrincipal principal() {
        return AgentPrincipal.current(this.properties);
    }

    /** Body for {@code POST /api/v1/sessions}. {@code compatibilityMode} may be omitted. */
    public record CreateSessionRequest(ModelProvider provider, CompatibilityMode compatibilityMode) {
    }

    /** Body for {@code PATCH /api/v1/sessions/{id}}. */
    public record PatchSessionRequest(ModelProvider provider, CompatibilityMode compatibilityMode) {
    }

    /**
     * Session returned by create, get, and patch. {@code id} is the path value
     * for later calls. There is no {@code sessionId} field.
     */
    public record SessionView(String id, String provider, String compatibilityMode) {
        static SessionView from(AgentSessionEntity entity) {
            return new SessionView(entity.getSessionId(), entity.getProvider(), entity.getCompatibilityMode());
        }
    }

    /**
     * Recruiter text for {@code POST /api/v1/sessions/{id}/messages}.
     * Name the tool in the text when the provider is OpenRouter, for example
     * {@code Move application APP-1 to stage TECH_SCREEN. Call advanceApplication.}
     */
    public record MessageRequest(@NotBlank @Size(max = 4000) String text) {
    }
}
