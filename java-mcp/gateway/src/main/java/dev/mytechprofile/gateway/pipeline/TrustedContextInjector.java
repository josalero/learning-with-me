package dev.mytechprofile.gateway.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.CompiledTool;

/**
 * Injects trusted context fields after argument validation.
 *
 * <p>Trusted values always overwrite model-supplied keys for mapped fields
 * (defense in depth behind schema {@code additionalProperties: false}).
 */
@Component
public class TrustedContextInjector {

    /**
     * Returns a copy of validated arguments with context mappings applied.
     *
     * @param tool compiled tool
     * @param validated arguments that already passed schema validation
     * @param context invocation context
     * @return enriched argument map
     */
    public Map<String, Object> inject(
            CompiledTool tool, Map<String, Object> validated, ToolExecutionContext context) {
        Map<String, Object> enriched = new LinkedHashMap<>(validated);
        tool.contextMappings().forEach((field, expression) -> enriched.put(field, expression.resolve(context)));
        return enriched;
    }
}
