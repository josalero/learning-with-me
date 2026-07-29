package dev.mytechprofile.gateway.pipeline;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.config.ConfigurationException;

/**
 * Parses YAML {@code context-mappings} into the closed {@link ContextExpression} set.
 */
@Component
public class ContextExpressionParser {

    /**
     * Parses all mappings for a tool.
     *
     * @param raw mapping field → expression string; may be {@code null}
     * @return immutable map of expressions
     */
    public Map<String, ContextExpression> parseAll(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, ContextExpression> parsed = new LinkedHashMap<>();
        raw.forEach((field, expression) -> parsed.put(field, parse(field, expression)));
        return Map.copyOf(parsed);
    }

    private ContextExpression parse(String field, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new ConfigurationException(
                    "context-mapping for '%s' is blank".formatted(field));
        }
        String normalized = expression.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "${identity.tenantid}" -> new ContextExpression.TenantId();
            case "${identity.subject}" -> new ContextExpression.Subject();
            case "${correlationid}", "${identity.correlationid}" -> new ContextExpression.CorrelationId();
            default -> throw new ConfigurationException(
                    "unsupported context expression '%s' for field '%s'".formatted(expression, field));
        };
    }
}
