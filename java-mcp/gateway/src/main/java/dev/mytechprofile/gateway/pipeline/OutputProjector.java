package dev.mytechprofile.gateway.pipeline;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.model.OutputPolicy;

/**
 * Projects connector payloads through {@link OutputPolicy} before MCP transport.
 *
 * <p>Allowlist-first: fields not listed in {@code output.include} are removed.
 * Masked fields stay present but redacted.
 */
@Component
public class OutputProjector {

    private final ObjectMapper mapper;

    public OutputProjector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Applies output policy to a success payload.
     *
     * @param policy compiled output policy
     * @param source connector payload
     * @return projected payload plus removal/mask counts
     */
    public ProjectionResult apply(OutputPolicy policy, JsonNode source) {
        if (policy == null
                || (!policy.isAllowlistActive()
                        && (policy.mask() == null || policy.mask().isEmpty())
                        && policy.maximumItems() == null)) {
            return new ProjectionResult(source, 0, 0);
        }
        MaskCounter counter = new MaskCounter();
        JsonNode projected = project(policy, source, counter);
        return new ProjectionResult(projected, counter.removedCount(), counter.maskedCount());
    }

    private JsonNode project(OutputPolicy policy, JsonNode node, MaskCounter counter) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            int limit = policy.effectiveMaximumItems(node.size());
            for (int i = 0; i < limit; i++) {
                out.add(project(policy, node.get(i), counter));
            }
            return out;
        }
        if (!node.isObject()) {
            return node;
        }

        ObjectNode out = mapper.createObjectNode();
        node.propertyNames().forEach(field -> {
            JsonNode value = node.get(field);
            if (!policy.isIncluded(field)) {
                counter.incrementRemoved();
                return;
            }
            if (policy.isMasked(field)) {
                out.put(field, policy.mask(field, value));
                counter.incrementMasked();
                return;
            }
            out.set(field, project(policy, value, counter));
        });
        return out;
    }

    /** Result of projecting a tool payload. */
    public record ProjectionResult(JsonNode payload, int removedFieldCount, int maskedFieldCount) {}

    private static final class MaskCounter {
        private int removed;
        private int masked;

        void incrementRemoved() {
            removed++;
        }

        void incrementMasked() {
            masked++;
        }

        int removedCount() {
            return removed;
        }

        int maskedCount() {
            return masked;
        }
    }
}
