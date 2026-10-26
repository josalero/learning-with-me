package dev.mytechprofile.statefulagent.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.model.session.autoconfigure.SessionServiceDecorator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SanitizingSessionDecorator implements SessionServiceDecorator {

    @Override
    public SessionService decorate(SessionService sessionService) {
        return new SanitizingSessionService(sessionService);
    }

    static final class SanitizingSessionService extends ForwardingSessionService {

        SanitizingSessionService(SessionService delegate) {
            super(delegate);
        }

        @Override
        public List<SessionEvent> getEvents(String sessionId, EventFilter filter) {
            List<SessionEvent> events = super.getEvents(sessionId, filter);
            String provider = PromptSanitization.currentProvider();
            if (provider == null) {
                return events;
            }
            return events.stream().map(event -> rewrite(event, provider)).toList();
        }

        private static SessionEvent rewrite(SessionEvent event, String provider) {
            if (!event.hasMessage() || !(event.getMessage() instanceof AbstractMessage message)) {
                return event;
            }
            Message rewritten = MessageRewriter.rewrite(message, provider);
            if (rewritten == message) {
                return event;
            }
            return SessionEvent.builder()
                    .id(event.getId())
                    .sessionId(event.getSessionId())
                    .timestamp(event.getTimestamp())
                    .type(event.getType())
                    .message(rewritten)
                    .metadata(event.getMetadata())
                    .build();
        }
    }

    public static final class MessageRewriter {

        private MessageRewriter() {
        }

        public static Message rewrite(Message message, String provider) {
            if (!(message instanceof AbstractMessage abstractMessage)) {
                return message;
            }
            List<MessagePart> rewritten = new ArrayList<>();
            boolean changed = false;
            for (MessagePart part : abstractMessage.getParts()) {
                MessagePart next = rewritePart(part, provider);
                changed = changed || next != part;
                rewritten.add(next);
            }
            if (!changed) {
                return message;
            }
            return switch (message) {
                case UserMessage user -> UserMessage.builder().parts(rewritten).metadata(user.getMetadata()).build();
                case AssistantMessage assistant -> AssistantMessage.builder().parts(rewritten).properties(assistant.getMetadata()).build();
                case ToolResponseMessage tool -> ToolResponseMessage.builder().parts(rewritten).metadata(tool.getMetadata()).build();
                case SystemMessage system -> SystemMessage.builder().text(system.getText()).metadata(system.getMetadata()).build();
                default -> message;
            };
        }

        public static MessagePart rewritePart(MessagePart part, String provider) {
            if (!foreign(part, provider)) {
                return part;
            }
            Map<String, String> attributes = withoutProvider(part.attributes());
            return switch (part) {
                case UnknownPart ignored -> TextPart.of("[omitted incompatible part]");
                case TextPart text -> new TextPart(text.text(), null, attributes);
                case ReasoningPart reasoning -> new ReasoningPart(reasoning.text(), reasoning.summary(), null, attributes);
                case ToolCallPart call -> new ToolCallPart(call.toolCall(), null, attributes);
                case ToolResultPart result -> new ToolResultPart(result.id(), result.name(),
                        result.content().stream().map(content -> rewritePart(content, provider)).toList(),
                        result.isError(), null, attributes);
                case MediaPart media -> new MediaPart(media.media(), null, attributes);
                default -> TextPart.of("[omitted incompatible part]");
            };
        }

        public static boolean foreign(MessagePart part, String provider) {
            if (part instanceof UnknownPart unknown && !provider.equals(unknown.provider())) {
                return true;
            }
            if (part.payload() != null && !provider.equals(part.payload().provider())) {
                return true;
            }
            String tagged = part.attributes().get(MessagePart.PROVIDER_ATTRIBUTE);
            return tagged != null && !provider.equals(tagged);
        }

        private static Map<String, String> withoutProvider(Map<String, String> attributes) {
            Map<String, String> copy = new LinkedHashMap<>(attributes);
            copy.remove(MessagePart.PROVIDER_ATTRIBUTE);
            return Map.copyOf(copy);
        }
    }
}
