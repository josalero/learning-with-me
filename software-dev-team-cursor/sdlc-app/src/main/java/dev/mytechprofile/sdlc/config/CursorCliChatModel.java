package dev.mytechprofile.sdlc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LangChain4j {@link ChatModel} that shells out to the Cursor {@code agent} CLI.
 *
 * <p><strong>When to use:</strong> live runs instead of OpenRouter. Always uses {@code --mode ask}
 * so the CLI does not write files; Developer and Tech Lead still edit through LangChain4j tools.
 *
 * <p><strong>Example:</strong> a Developer request with {@code writeFile} in the tool list gets a
 * {@code type=tool_call} JSON reply, which this class turns into {@link ToolExecutionRequest}.
 */
public final class CursorCliChatModel implements ChatModel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String JSON_ONLY_RULES =
            """
            You are a JSON-only assistant inside a LangChain4j agent loop.
            Do not modify files yourself. Do not call Cursor write, shell, or MCP tools.
            Reply with a single JSON object and no markdown fences.
            """;

    private static final String TOOL_RULES =
            """
            If you need one of the listed tools first, reply:
            {"type":"tool_call","name":"<toolName>","arguments":{...}}
            When you are finished, reply:
            {"type":"final","content": <the role JSON object>}
            """;

    private final String model;
    private final String apiKey;
    private final String cliCommand;
    private final Duration timeout;
    private final Path workingDirectory;
    private final CursorCliExecutor executor;

    /**
     * Creates a CLI-backed model for one role slug.
     *
     * @param properties home, key, command, and timeout
     * @param model Cursor model id such as {@code composer-2.5}
     * @param executor process runner
     */
    public CursorCliChatModel(SdlcProperties properties, String model, CursorCliExecutor executor) {
        this.model = model == null || model.isBlank() ? properties.modelFast() : model;
        this.apiKey = properties.cursorApiKey();
        this.cliCommand = properties.cursorCliCommand();
        this.timeout = properties.llmTimeout();
        this.workingDirectory = properties.home();
        this.executor = executor;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        List<ToolSpecification> tools =
                chatRequest.toolSpecifications() == null ? List.of() : chatRequest.toolSpecifications();
        String prompt = buildPrompt(chatRequest.messages(), tools);
        String stdout = executor.execute(argv(prompt), workingDirectory, timeout);
        AiMessage message = parse(stdout, tools);
        return ChatResponse.builder().aiMessage(message).build();
    }

    List<String> argv(String prompt) {
        List<String> command = new ArrayList<>();
        command.add(cliCommand);
        command.add("-p");
        command.add("--mode");
        command.add("ask");
        command.add("--output-format");
        command.add("text");
        command.add("--trust");
        command.add("--workspace");
        command.add(workingDirectory.toString());
        command.add("--model");
        command.add(model);
        if (apiKey != null && !apiKey.isBlank()) {
            command.add("--api-key");
            command.add(apiKey);
        }
        command.add(prompt);
        return List.copyOf(command);
    }

    static String buildPrompt(List<ChatMessage> messages, List<ToolSpecification> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(JSON_ONLY_RULES);
        if (!tools.isEmpty()) {
            prompt.append(TOOL_RULES);
            prompt.append("Available tools:\n");
            for (ToolSpecification tool : tools) {
                prompt.append("- name: ").append(tool.name()).append('\n');
                if (tool.description() != null && !tool.description().isBlank()) {
                    prompt.append("  description: ").append(tool.description()).append('\n');
                }
                if (tool.parameters() != null) {
                    prompt.append("  parameters: ").append(tool.toJson()).append('\n');
                }
            }
        }
        prompt.append('\n');
        for (ChatMessage message : messages) {
            prompt.append(format(message)).append("\n\n");
        }
        return prompt.toString();
    }

    static AiMessage parse(String stdout, List<ToolSpecification> tools) {
        String object = JsonPayloads.firstObject(stdout);
        if (object == null) {
            return AiMessage.from(stdout == null ? "" : stdout.trim());
        }
        try {
            JsonNode root = JSON.readTree(object);
            Set<String> toolNames = tools.stream().map(ToolSpecification::name).collect(Collectors.toSet());
            if (isToolCall(root, toolNames)) {
                String name = root.path("name").asText();
                JsonNode arguments = root.get("arguments");
                String argsJson = arguments == null || arguments.isNull() || arguments.isMissingNode()
                        ? "{}"
                        : arguments.toString();
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .id("call-" + UUID.randomUUID())
                        .name(name)
                        .arguments(argsJson)
                        .build();
                return AiMessage.from(request);
            }
            if ("final".equals(root.path("type").asText()) && root.has("content")) {
                JsonNode content = root.get("content");
                String text = content.isTextual() ? content.asText() : content.toString();
                return AiMessage.from(text);
            }
            return AiMessage.from(object);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return AiMessage.from(object);
        }
    }

    private static boolean isToolCall(JsonNode root, Set<String> toolNames) {
        if (toolNames.isEmpty()) {
            return false;
        }
        String type = root.path("type").asText("");
        if ("final".equals(type)) {
            return false;
        }
        String name = root.path("name").asText("");
        if (!toolNames.contains(name)) {
            return false;
        }
        return "tool_call".equals(type) || root.has("arguments");
    }

    private static String format(ChatMessage message) {
        return switch (message.type()) {
            case SYSTEM -> "SYSTEM:\n" + ((SystemMessage) message).text();
            case USER -> "USER:\n" + userText((UserMessage) message);
            case AI -> "ASSISTANT:\n" + aiText((AiMessage) message);
            case TOOL_EXECUTION_RESULT -> toolText((ToolExecutionResultMessage) message);
            case CUSTOM -> message.toString();
        };
    }

    private static String userText(UserMessage message) {
        return message.hasSingleText() ? message.singleText() : message.toString();
    }

    private static String aiText(AiMessage message) {
        if (message.hasToolExecutionRequests()) {
            return message.toolExecutionRequests().toString();
        }
        return message.text() == null ? "" : message.text();
    }

    private static String toolText(ToolExecutionResultMessage message) {
        return "TOOL_RESULT name=" + message.toolName() + ":\n" + message.text();
    }
}
