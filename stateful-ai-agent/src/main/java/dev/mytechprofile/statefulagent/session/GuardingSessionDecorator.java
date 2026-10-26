package dev.mytechprofile.statefulagent.session;

import java.util.List;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;
import org.springframework.ai.model.session.autoconfigure.SessionServiceDecorator;

@Component
@Order(0)
public class GuardingSessionDecorator implements SessionServiceDecorator {

    private final AgentProperties properties;

    public GuardingSessionDecorator(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public SessionService decorate(SessionService sessionService) {
        return new GuardingSessionService(sessionService, this.properties);
    }

    static final class GuardingSessionService extends ForwardingSessionService {

        private final AgentProperties properties;

        GuardingSessionService(SessionService delegate, AgentProperties properties) {
            super(delegate);
            this.properties = properties;
        }

        @Override
        public Session findById(String sessionId) {
            Session session = super.findById(sessionId);
            if (session == null || allowed(session)) {
                return session;
            }
            return null;
        }

        @Override
        public List<SessionEvent> getEvents(String sessionId, EventFilter filter) {
            if (findById(sessionId) == null && super.findById(sessionId) != null) {
                return List.of();
            }
            return super.getEvents(sessionId, filter);
        }

        @Override
        public void appendEvent(SessionEvent event) {
            if (super.findById(event.getSessionId()) != null && findById(event.getSessionId()) == null) {
                throw new SessionNotFoundException();
            }
            super.appendEvent(event);
        }

        private boolean allowed(Session session) {
            if (SessionAccess.isSystem() || !AgentPrincipal.authenticated()) {
                return true;
            }
            AgentPrincipal principal = AgentPrincipal.current(this.properties);
            Object tenant = session.metadata().get("tenant");
            return principal.username().equals(SessionAttributes.principalName(session))
                    && principal.tenantId().equals(tenant);
        }
    }
}
