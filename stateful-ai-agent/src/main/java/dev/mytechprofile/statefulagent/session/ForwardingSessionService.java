package dev.mytechprofile.statefulagent.session;

import java.time.Instant;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;

class ForwardingSessionService implements SessionService {

    private final SessionService delegate;

    ForwardingSessionService(SessionService delegate) {
        this.delegate = delegate;
    }

    protected SessionService delegate() {
        return this.delegate;
    }

    @Override
    public Session create(CreateSessionRequest request) {
        return this.delegate.create(request);
    }

    @Override
    public Session findById(String sessionId) {
        return this.delegate.findById(sessionId);
    }

    @Override
    public List<Session> findByMetadata(String name, String value) {
        return this.delegate.findByMetadata(name, value);
    }

    @Override
    public void delete(String sessionId) {
        this.delegate.delete(sessionId);
    }

    @Override
    public int deleteExpiredSessions(Instant before) {
        return this.delegate.deleteExpiredSessions(before);
    }

    @Override
    public void appendEvent(SessionEvent event) {
        this.delegate.appendEvent(event);
    }

    @Override
    public List<SessionEvent> getEvents(String sessionId, EventFilter filter) {
        return this.delegate.getEvents(sessionId, filter);
    }

    @Override
    public CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
        return this.delegate.compact(sessionId, trigger, strategy);
    }

    @Override
    public void appendMessage(String sessionId, Message message) {
        this.delegate.appendMessage(sessionId, message);
    }

    @Override
    public List<Message> getActiveMessages(String sessionId) {
        return this.delegate.getActiveMessages(sessionId);
    }
}
