package dev.mytechprofile.langchain122.provider;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Reads provider keys and model overrides from environment variables.
 *
 * <p>Each live client asks for a key first and stays offline when it is missing. Tests pass a map
 * lookup instead of {@link System#getenv(String)}, so no real key is needed:
 *
 * <pre>{@code
 * ProviderEnvironment env = new ProviderEnvironment(Map.of("OPENROUTER_API_KEY", "test-key")::get);
 * env.value("OPENROUTER_API_KEY");                                   // Optional[test-key]
 * env.valueOr("OPENROUTER_DECISION_MODEL", "openai/gpt-6-luna-decisions"); // the default
 * }</pre>
 *
 * Blank values count as missing. Values are never logged.
 *
 * @param lookup variable name to value, or {@code null} when unset
 */
public record ProviderEnvironment(UnaryOperator<String> lookup) {

    /** The process environment. */
    public static ProviderEnvironment system() {
        return new ProviderEnvironment(System::getenv);
    }

    /**
     * @param name variable such as {@code OPENROUTER_API_KEY}
     * @return the value, or empty when unset or blank
     */
    public Optional<String> value(String name) {
        return Optional.ofNullable(lookup.apply(name)).filter(value -> !value.isBlank());
    }

    /**
     * @param name variable such as {@code OPENROUTER_CHAT_MODEL}
     * @param fallback returned when the variable is unset or blank
     */
    public String valueOr(String name, String fallback) {
        return value(name).orElse(fallback);
    }
}
