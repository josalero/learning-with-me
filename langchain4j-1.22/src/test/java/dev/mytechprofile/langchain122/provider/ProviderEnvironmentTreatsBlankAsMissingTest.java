package dev.mytechprofile.langchain122.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mytechprofile.langchain122.screening.OpenAiApplicationScreen;
import dev.mytechprofile.langchain122.screening.openrouter.OpenRouterApplicationScreen;
import dev.mytechprofile.langchain122.posting.OpenRouterPostingSkills;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProviderEnvironmentTreatsBlankAsMissingTest {

    @Test
    void blankValueFallsBackToTheDefaultModel() {
        ProviderEnvironment env = new ProviderEnvironment(Map.of("OPENROUTER_CHAT_MODEL", "  ")::get);

        assertEquals(Optional.empty(), env.value("OPENROUTER_CHAT_MODEL"));
        assertEquals("openai/gpt-4o-mini", env.valueOr("OPENROUTER_CHAT_MODEL", "openai/gpt-4o-mini"));
    }

    @Test
    void liveClientsStayOfflineWithoutAKey() {
        ProviderEnvironment empty = new ProviderEnvironment(Map.<String, String>of()::get);

        assertTrue(OpenAiApplicationScreen.whenKeyPresent(empty).isEmpty());
        assertTrue(OpenRouterApplicationScreen.whenKeyPresent(empty).isEmpty());
        assertTrue(OpenRouterPostingSkills.whenKeyPresent(empty).isEmpty());
    }

    @Test
    void openRouterScreenIsBuiltWhenTheKeyIsPresent() {
        ProviderEnvironment env = new ProviderEnvironment(Map.of("OPENROUTER_API_KEY", "test-key")::get);

        assertTrue(OpenRouterApplicationScreen.whenKeyPresent(env).isPresent());
        assertTrue(OpenRouterPostingSkills.whenKeyPresent(env).isPresent());
    }
}
