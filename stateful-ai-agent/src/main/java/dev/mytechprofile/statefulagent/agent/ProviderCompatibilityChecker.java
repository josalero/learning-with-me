package dev.mytechprofile.statefulagent.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.session.SanitizingSessionDecorator.MessageRewriter;

@Component
public class ProviderCompatibilityChecker {

    public List<String> issues(List<Message> messages, String providerWireName) {
        List<String> issues = new ArrayList<>();
        for (Message message : messages) {
            if (!(message instanceof AbstractMessage abstractMessage)) {
                continue;
            }
            for (var part : abstractMessage.getParts()) {
                if (MessageRewriter.foreign(part, providerWireName)) {
                    issues.add(part.getClass().getSimpleName() + " cannot be replayed to " + providerWireName);
                }
            }
        }
        return List.copyOf(issues);
    }
}
