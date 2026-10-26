package dev.mytechprofile.statefulagent.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component
public class ForeignStubChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        AssistantMessage message = AssistantMessage.builder()
                .parts(List.of(
                        new TextPart("foreign answer", new OpaquePayload("stub-foreign", "signature", "signed-text"),
                                Map.of("provider", "stub-foreign")),
                        new UnknownPart("stub-foreign", "vendor-block", "{}",
                                new OpaquePayload("stub-foreign", "raw", "{}"), Map.of())))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
