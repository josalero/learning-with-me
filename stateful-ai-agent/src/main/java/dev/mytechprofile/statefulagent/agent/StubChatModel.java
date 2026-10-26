package dev.mytechprofile.statefulagent.agent;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component
public class StubChatModel implements ChatModel {

    private static final Pattern APPLICATION = Pattern.compile("APP-[A-Za-z0-9-]+");
    private static final Pattern LEVEL = Pattern.compile("L(\\d+)");
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final AtomicInteger callIds = new AtomicInteger();
    private final CopyOnWriteArrayList<Prompt> prompts = new CopyOnWriteArrayList<>();

    @Override
    public ChatResponse call(Prompt prompt) {
        this.prompts.add(prompt);
        Message last = prompt.getInstructions().get(prompt.getInstructions().size() - 1);
        if (last instanceof ToolResponseMessage toolResponse) {
            String data = toolResponse.getResponses().isEmpty() ? "" : toolResponse.getResponses().get(0).responseData();
            return text("Done: " + data);
        }
        String text = last instanceof UserMessage user ? user.getText() : prompt.getContents();
        if (text != null && text.toLowerCase(Locale.ROOT).contains("summarize the conversation")) {
            return text("Summary of earlier turns.");
        }
        if (text == null) {
            return text("ack");
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("note:")) {
            return text("ack");
        }
        String application = application(text);
        if (normalized.contains("status")) {
            return tool("getApplication", "{\"applicationId\":\"" + application + "\"}");
        }
        if (normalized.contains("history")) {
            return tool("listHiringActions", "{\"applicationId\":\"" + application + "\"}");
        }
        if (normalized.contains("reject")) {
            return tool("rejectApplication", "{\"applicationId\":\"" + application + "\",\"reason\":\"" + rejectReason(normalized) + "\"}");
        }
        if (normalized.contains("accepted")) {
            return tool("recordOfferResponse", "{\"applicationId\":\"" + application + "\",\"response\":\"ACCEPTED\"}");
        }
        if (normalized.contains("declined")) {
            return tool("recordOfferResponse", "{\"applicationId\":\"" + application + "\",\"response\":\"DECLINED\"}");
        }
        if (normalized.contains("extend offer") || normalized.contains("extend the offer")) {
            return tool("extendOffer", "{\"applicationId\":\"" + application + "\",\"level\":\"" + level(text)
                    + "\",\"baseSalary\":" + amount(text) + "}");
        }
        if (normalized.contains("was strong yes") || normalized.contains("was yes") || normalized.contains("was no")
                || normalized.contains("was strong no")) {
            String round = normalized.contains("onsite") ? "ONSITE" : "TECH_SCREEN";
            return tool("recordScorecard", "{\"applicationId\":\"" + application + "\",\"round\":\"" + round
                    + "\",\"recommendation\":\"" + recommendation(normalized) + "\"}");
        }
        if (normalized.contains("schedule") || normalized.contains("move") || normalized.contains("advance")) {
            return tool("advanceApplication", "{\"applicationId\":\"" + application + "\",\"stage\":\"" + stage(normalized) + "\"}");
        }
        return text("I can read an application, schedule a tech screen, record a scorecard, advance a stage, extend an offer, or reject.");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }

    public List<Prompt> prompts() {
        return List.copyOf(this.prompts);
    }

    public void reset() {
        this.prompts.clear();
    }

    private ChatResponse tool(String name, String arguments) {
        AssistantMessage message = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + this.callIds.incrementAndGet(), "function", name, arguments)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static String application(String text) {
        Matcher matcher = APPLICATION.matcher(text);
        return matcher.find() ? matcher.group() : "APP-1";
    }

    private static String stage(String normalized) {
        if (normalized.contains("offer")) {
            return "OFFER";
        }
        if (normalized.contains("onsite")) {
            return "ONSITE";
        }
        if (normalized.contains("tech")) {
            return "TECH_SCREEN";
        }
        if (normalized.contains("recruiter")) {
            return "RECRUITER_SCREEN";
        }
        return "TECH_SCREEN";
    }

    private static String recommendation(String normalized) {
        if (normalized.contains("strong yes")) {
            return "STRONG_YES";
        }
        if (normalized.contains("strong no")) {
            return "STRONG_NO";
        }
        if (normalized.contains(" yes")) {
            return "YES";
        }
        return "NO";
    }

    private static String rejectReason(String normalized) {
        if (normalized.contains("withdrew")) {
            return "CANDIDATE_WITHDREW";
        }
        if (normalized.contains("role")) {
            return "ROLE_MISMATCH";
        }
        return "BAR_NOT_MET";
    }

    private static String level(String text) {
        Matcher matcher = LEVEL.matcher(text);
        return matcher.find() ? "L" + matcher.group(1) : "L4";
    }

    private static String amount(String text) {
        Matcher matcher = AMOUNT.matcher(text);
        String last = "150000";
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }
}
