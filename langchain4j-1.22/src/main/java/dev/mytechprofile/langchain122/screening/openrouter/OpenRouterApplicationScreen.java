package dev.mytechprofile.langchain122.screening.openrouter;

import dev.langchain4j.service.decision.DecisionServices;
import dev.mytechprofile.langchain122.provider.ProviderEnvironment;
import dev.mytechprofile.langchain122.screening.ApplicationScreen;
import java.util.Optional;

/**
 * Builds {@link ApplicationScreen} on OpenRouter when {@code OPENROUTER_API_KEY} is set.
 *
 * <p>The default model is {@link OpenRouterDecisionModel#GPT_6_LUNA_DECISIONS}. Override it
 * with {@code OPENROUTER_DECISION_MODEL}. Creating the screen does not send {@code app-1042}.
 * The first {@link ApplicationScreen#screen} call does.
 *
 * <pre>{@code
 * Optional<ApplicationScreen> live = OpenRouterApplicationScreen.whenKeyPresent(ProviderEnvironment.system());
 * live.ifPresent(screen -> screen.screen(
 *         ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"),
 *         AtsSample.RECRUITER_NOTE));
 * }</pre>
 *
 * <p>A missing or blank key returns {@link Optional#empty()}. A later HTTP 401, timeout, or
 * unreadable body throws {@link OpenRouterDecisionException} from {@code screen}.
 */
public final class OpenRouterApplicationScreen {

    /** Bearer token variable shared with the OpenRouter chat sample. */
    public static final String API_KEY = "OPENROUTER_API_KEY";

    /** Optional override for {@link OpenRouterDecisionModel#GPT_6_LUNA_DECISIONS}. */
    public static final String MODEL = "OPENROUTER_DECISION_MODEL";

    private OpenRouterApplicationScreen() {}

    /**
     * @param env where to read {@link #API_KEY} and {@link #MODEL}
     * @return a screen aimed at OpenRouter, or empty when the key is missing
     */
    public static Optional<ApplicationScreen> whenKeyPresent(ProviderEnvironment env) {
        return env.value(API_KEY)
                .map(apiKey -> screen(apiKey, env.valueOr(MODEL, OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS)));
    }

    /**
     * @param apiKey OpenRouter bearer token
     * @param modelName catalog id, normally {@code openai/gpt-6-luna-decisions}
     * @return a screen that posts to {@link OpenRouterDecisionModel#DECISIONS_ENDPOINT} on the first call
     */
    public static ApplicationScreen screen(String apiKey, String modelName) {
        return DecisionServices.create(
                ApplicationScreen.class,
                new OpenRouterDecisionModel(apiKey, modelName, OpenRouterDecisionModel.DECISIONS_ENDPOINT));
    }
}
