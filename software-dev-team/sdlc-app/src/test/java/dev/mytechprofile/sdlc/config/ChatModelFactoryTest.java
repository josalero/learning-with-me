package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mytechprofile.sdlc.catalog.RoleSpec;
import dev.mytechprofile.sdlc.domain.RoleKind;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatModelFactoryTest {

    @TempDir
    Path home;

    @Test
    void modelFor_whenOffline_returnsScriptedModel() {
        ChatModelFactory factory = new ChatModelFactory(TestHomes.properties(home));
        RoleSpec role = new RoleSpec("developer", RoleKind.DEVELOPER, "fast", "prompts/developer.md", 0);

        assertThat(factory.modelFor(role)).isSameAs(ScriptedChatModel.INSTANCE);
    }

    @Test
    void modelFor_whenLive_buildsOpenAiChatModelWithConfiguredTimeout() {
        SdlcProperties properties = new SdlcProperties(
                home,
                "test-key",
                "https://openrouter.ai/api/v1",
                256,
                Duration.ofMinutes(5),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                false);
        ChatModelFactory factory = new ChatModelFactory(properties);
        RoleSpec role = new RoleSpec("developer", RoleKind.DEVELOPER, "fast", "prompts/developer.md", 0);

        assertThat(factory.modelFor(role)).isInstanceOf(ThinkingJsonChatModel.class);
        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void usesJsonObjectResponse_whenPrReviewer_isFalseSoThinkingModelsKeepContent() {
        RoleSpec reviewer = new RoleSpec("pr-reviewer", RoleKind.PR_REVIEWER, "strong", "prompts/pr-reviewer.md", 0);

        assertThat(ChatModelFactory.usesJsonObjectResponse(reviewer)).isFalse();
        assertThat(ChatModelFactory.emptyFallbackJson(reviewer)).contains("APPROVE");
    }
}
