package dev.mytechprofile.statefulagent.session;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionService;

import org.springframework.stereotype.Service;

import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;

@Service
public class AgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionService.class);

    private final SessionService sessionService;
    private final AgentSessionRepository sessions;

    public AgentSessionService(SessionService sessionService, AgentSessionRepository sessions) {
        this.sessionService = sessionService;
        this.sessions = sessions;
    }

    public AgentSessionEntity create(AgentPrincipal principal, ModelProvider provider, CompatibilityMode mode) {
        Session session = this.sessionService.create(CreateSessionRequest.builder()
                .metadata(SessionAttributes.PRINCIPAL_NAME, principal.username())
                .metadata("tenant", principal.tenantId())
                .metadata(SessionAttributes.AGENT_NAME, "hiring-agent")
                .metadata(SessionAttributes.AGENT_MODEL, provider.name())
                .build());
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setSessionId(session.id());
        entity.setTenantId(principal.tenantId());
        entity.setOwnerName(principal.username());
        entity.setProvider(provider.name());
        entity.setCompatibilityMode(mode.name());
        entity.setCreatedAt(Instant.now());
        AgentSessionEntity saved = this.sessions.save(entity);
        log.info("{} opened hiring session {} on {} ({}) for {}.", principal.username(), saved.getSessionId(), provider, mode,
                saved.getTenantId());
        return saved;
    }

    public AgentSessionEntity requireOwned(String sessionId, AgentPrincipal principal) {
        AgentSessionEntity entity = this.sessions.findById(sessionId).orElseThrow(() -> {
            log.info("Session {} does not exist.", sessionId);
            return new SessionNotFoundException();
        });
        if (!entity.getOwnerName().equals(principal.username()) || !entity.getTenantId().equals(principal.tenantId())) {
            log.info("{} cannot open session {}. Only the owner can read it.", principal.username(), sessionId);
            throw new SessionNotFoundException();
        }
        return entity;
    }

    public AgentSessionEntity requireVisible(String sessionId, AgentPrincipal principal) {
        AgentSessionEntity entity = this.sessions.findById(sessionId).orElseThrow(() -> {
            log.info("Session {} does not exist.", sessionId);
            return new SessionNotFoundException();
        });
        if (!entity.getTenantId().equals(principal.tenantId())) {
            log.info("{} cannot open session {}. It belongs to another tenant.", principal.username(), sessionId);
            throw new SessionNotFoundException();
        }
        boolean owner = entity.getOwnerName().equals(principal.username());
        if (!owner && !principal.approver()) {
            log.info("{} cannot open session {}. Only the owner or an approver can.", principal.username(), sessionId);
            throw new SessionNotFoundException();
        }
        return entity;
    }

    public AgentSessionEntity updateProvider(AgentSessionEntity entity, ModelProvider provider, CompatibilityMode mode) {
        entity.setProvider(provider.name());
        if (mode != null) {
            entity.setCompatibilityMode(mode.name());
        }
        log.info("Session {} now uses {} ({}).", entity.getSessionId(), entity.getProvider(), entity.getCompatibilityMode());
        return this.sessions.save(entity);
    }

    public List<AgentSessionEntity> findAll() {
        return this.sessions.findAll();
    }
}
