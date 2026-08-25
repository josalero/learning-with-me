package dev.mytechprofile.sdlc.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.mytechprofile.sdlc.catalog.RoleSpec;

/**
 * Builds per-role Cursor CLI chat models from a {@link RoleSpec}.
 *
 * <p><strong>When to use:</strong> production runs. {@code sdlc.offline=true} returns
 * {@link ScriptedChatModel}. Tests may also inject a scripted {@link ChatModel}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * ChatModel model = factory.modelFor(developerRole);
 * }</pre>
 */
public final class ChatModelFactory {

    private final SdlcProperties properties;
    private final CursorCliExecutor executor;

    /**
     * Creates a factory using Cursor CLI settings from {@code properties}.
     *
     * @param properties API key, CLI command, and per-request timeout
     */
    public ChatModelFactory(SdlcProperties properties) {
        this(properties, new DefaultCursorCliExecutor());
    }

    /**
     * Creates a factory with a custom process runner.
     *
     * @param properties API key, CLI command, and per-request timeout
     * @param executor CLI process runner
     */
    public ChatModelFactory(SdlcProperties properties, CursorCliExecutor executor) {
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * Returns a chat model for {@code role}.
     *
     * @param role team role with model slug and temperature
     * @return Cursor CLI chat model wrapped for JSON recovery
     */
    public ChatModel modelFor(RoleSpec role) {
        if (properties.offline()) {
            return ScriptedChatModel.INSTANCE;
        }
        if (properties.cursorApiKey() == null || properties.cursorApiKey().isBlank()) {
            throw new IllegalStateException(
                    "CURSOR_API_KEY is not set. Copy .env.example to .env and add a key from https://cursor.com/dashboard?tab=integrations, or set SDLC_OFFLINE=true");
        }
        CursorCliChatModel cli = new CursorCliChatModel(properties, role.model(), executor);
        return new ThinkingJsonChatModel(cli, emptyFallbackJson(role));
    }

    static String emptyFallbackJson(RoleSpec role) {
        return switch (role.kind()) {
            case PR_REVIEWER -> "{\"decision\":\"APPROVE\",\"findings\":[],\"blockingCount\":0}";
            case QA -> "{\"decision\":\"FAIL\",\"score\":0,\"results\":[],\"missingTests\":[]}";
            case STAKEHOLDER -> "{\"decision\":\"APPROVED\",\"reasons\":[],\"followUps\":[]}";
            case PRODUCT_OWNER ->
                "{\"title\":\"Requested feature\",\"problem\":\"\",\"userStories\":[],\"acceptanceCriteria\":[],\"outOfScope\":[],\"priority\":\"should\"}";
            case DEVELOPER, TECH_LEAD -> "{}";
        };
    }
}
