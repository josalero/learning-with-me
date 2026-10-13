package dev.mytechprofile.langchain122.screening;

import dev.langchain4j.model.openai.OpenAiDecisionModel;
import dev.langchain4j.model.openai.OpenAiDecisionModelName;
import dev.langchain4j.service.decision.DecisionServices;
import dev.mytechprofile.langchain122.provider.ProviderEnvironment;
import java.util.Optional;

/**
 * Builds the 1.22 OpenAI decision client for {@link ApplicationScreen} when {@code OPENAI_API_KEY} is set.
 * Building the client does not send the application.
 *
 * <pre>{@code
 * OpenAiApplicationScreen.whenKeyPresent(ProviderEnvironment.system())
 *         .ifPresent(screen -> screen.screen(portfolio, AtsSample.RECRUITER_NOTE));
 * }</pre>
 */
public final class OpenAiApplicationScreen {

    /** Bearer token variable read by {@link #whenKeyPresent}. */
    public static final String API_KEY = "OPENAI_API_KEY";

    private OpenAiApplicationScreen() {}

    /**
     * @param env where to read {@link #API_KEY}
     * @return a screen aimed at {@code gpt-6-luna}, or empty when the key is missing
     */
    public static Optional<ApplicationScreen> whenKeyPresent(ProviderEnvironment env) {
        return env.value(API_KEY).map(apiKey -> DecisionServices.create(
                ApplicationScreen.class,
                OpenAiDecisionModel.builder()
                        .apiKey(apiKey)
                        .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
                        .build()));
    }
}
