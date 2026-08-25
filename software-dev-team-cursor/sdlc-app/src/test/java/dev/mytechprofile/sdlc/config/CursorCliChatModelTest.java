package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CursorCliChatModelTest {

    @TempDir
    Path home;

    @Test
    void doChat_whenCliReturnsRoleJson_returnsTextWithoutToolCalls() {
        AtomicReference<List<String>> captured = new AtomicReference<>();
        CursorCliChatModel model = model((argv, cwd, timeout) -> {
            captured.set(argv);
            return """
                    here is the brief
                    ```json
                    {"title":"404","problem":"unknown id","userStories":[],"acceptanceCriteria":[],"outOfScope":[],"priority":"must"}
                    ```
                    """;
        });
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Return JSON for FeatureBrief"))
                .build();

        String text = model.chat(request).aiMessage().text();

        assertThat(text).contains("\"title\":\"404\"");
        assertThat(captured.get()).contains("-p", "--mode", "ask", "--output-format", "text", "--trust", "--model");
        assertThat(captured.get()).doesNotContain("--force", "--yolo");
        assertThat(captured.get()).contains("test-key");
        assertThat(captured.get().getFirst()).isEqualTo("agent");
    }

    @Test
    void doChat_whenCliReturnsToolCall_buildsToolExecutionRequest() {
        CursorCliChatModel model = model(
                (argv, cwd, timeout) ->
                        "{\"type\":\"tool_call\",\"name\":\"readFile\",\"arguments\":{\"relativePath\":\"UserController.java\"}}");
        ToolSpecification readFile = ToolSpecification.builder()
                .name("readFile")
                .description("Read a UTF-8 text file")
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from("Implement the spec."), UserMessage.from("Use readFile"))
                .toolSpecifications(readFile)
                .build();

        AiMessage message = model.chat(request).aiMessage();

        assertThat(message.hasToolExecutionRequests()).isTrue();
        assertThat(message.toolExecutionRequests().getFirst().name()).isEqualTo("readFile");
        assertThat(message.toolExecutionRequests().getFirst().arguments()).contains("UserController.java");
    }

    @Test
    void doChat_whenCliReturnsFinalEnvelope_unwrapsContent() {
        CursorCliChatModel model = model(
                (argv, cwd, timeout) ->
                        "{\"type\":\"final\",\"content\":{\"filesTouched\":[\"UserController.java\"],\"rationale\":\"404\",\"notes\":\"\"}}");
        ToolSpecification writeFile =
                ToolSpecification.builder().name("writeFile").build();
        ChatRequest request = ChatRequest.builder()
                .messages(ToolExecutionResultMessage.from("1", "writeFile", "wrote"))
                .toolSpecifications(writeFile)
                .build();

        String text = model.chat(request).aiMessage().text();

        assertThat(text).contains("UserController.java").contains("404");
        assertThat(model.chat(request).aiMessage().hasToolExecutionRequests()).isFalse();
    }

    @Test
    void argv_whenApiKeyBlank_omitsApiKeyFlag() {
        SdlcProperties properties = new SdlcProperties(
                home,
                "",
                "agent",
                256,
                Duration.ofMinutes(3),
                "composer-2.5",
                "composer-2.5",
                8_000,
                Duration.ofMinutes(1),
                false);
        CursorCliChatModel model = new CursorCliChatModel(properties, "composer-2.5", (argv, cwd, timeout) -> "");

        List<String> argv = model.argv("hello");

        assertThat(argv).doesNotContain("--api-key");
        assertThat(argv.getLast()).isEqualTo("hello");
    }

    private CursorCliChatModel model(CursorCliExecutor executor) {
        SdlcProperties properties = new SdlcProperties(
                home,
                "test-key",
                "agent",
                256,
                Duration.ofMinutes(3),
                "composer-2.5",
                "composer-2.5",
                8_000,
                Duration.ofMinutes(1),
                false);
        return new CursorCliChatModel(properties, "composer-2.5", executor);
    }
}
