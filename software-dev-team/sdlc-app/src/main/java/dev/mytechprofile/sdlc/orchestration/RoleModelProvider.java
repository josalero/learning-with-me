package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.model.chat.ChatModel;
import dev.mytechprofile.sdlc.catalog.RoleSpec;

/**
 * Resolves a chat model for one team role.
 *
 * <p><strong>When to use:</strong> production uses {@link
 * dev.mytechprofile.sdlc.config.ChatModelFactory}; tests pass a scripted model.
 *
 * <p><strong>Example:</strong> {@code provider.modelFor(developerRole)}.
 */
@FunctionalInterface
public interface RoleModelProvider {

    /**
     * Returns the chat model for {@code role}.
     *
     * @param role team role with model slug
     * @return chat model
     */
    ChatModel modelFor(RoleSpec role);
}
