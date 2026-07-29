package dev.mytechprofile.gateway.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Projects connector operation schemas into model-visible JSON Schema.
 *
 * <p><strong>When to use:</strong> called by {@link ToolCompiler} during startup.
 * Applies {@code include} narrowing, strips context fields, and applies
 * narrowing-only overrides.
 */
@Component
public class SchemaProjector {

    private final ObjectMapper mapper;

    public SchemaProjector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Builds the model-visible JSON Schema for a tool.
     *
     * @param source schema from the connector operation descriptor
     * @param include fields the model may supply; null/empty means all non-context fields
     * @param contextFields fields injected from trusted context (never model-visible)
     * @param overrides per-field constraint tightening
     * @return projected schema with {@code additionalProperties: false}
     */
    public JsonNode project(
            JsonNode source,
            List<String> include,
            Set<String> contextFields,
            Map<String, Map<String, Object>> overrides) {

        ObjectNode result = mapper.createObjectNode();
        result.put("type", "object");
        result.put("additionalProperties", false);

        JsonNode sourcePropsNode = source.path("properties");
        if (!sourcePropsNode.isObject()) {
            throw new ConfigurationException("operation schema is missing object properties");
        }
        ObjectNode sourceProps = (ObjectNode) sourcePropsNode;
        ObjectNode targetProps = result.putObject("properties");

        List<String> selected = (include == null || include.isEmpty())
                ? fieldNames(sourceProps)
                : List.copyOf(include);

        Map<String, Map<String, Object>> safeOverrides = overrides == null ? Map.of() : overrides;

        for (String field : selected) {
            if (contextFields.contains(field)) {
                continue;
            }
            JsonNode fieldSchema = sourceProps.get(field);
            if (fieldSchema == null || fieldSchema.isMissingNode() || fieldSchema.isNull()) {
                throw new ConfigurationException(
                        "field '%s' is not present in the operation schema".formatted(field));
            }
            JsonNode copy = fieldSchema.deepCopy();
            ObjectNode projected = copy instanceof ObjectNode objectNode
                    ? objectNode
                    : objectNodeFrom(copy);
            applyOverride(projected, field, safeOverrides.get(field));
            targetProps.set(field, projected);
        }

        ArrayNode required = result.putArray("required");
        JsonNode sourceRequired = source.path("required");
        if (sourceRequired.isArray()) {
            sourceRequired.forEach(node -> {
                String name = node.asString();
                if (targetProps.has(name)) {
                    required.add(name);
                } else if (!contextFields.contains(name)) {
                    throw new ConfigurationException(
                            "required field '%s' is excluded but not context-mapped".formatted(name));
                }
            });
        }
        if (required.isEmpty()) {
            result.remove("required");
        }
        return result;
    }

    private ObjectNode objectNodeFrom(JsonNode node) {
        ObjectNode objectNode = mapper.createObjectNode();
        if (node != null && node.isObject()) {
            node.propertyNames().forEach(name -> objectNode.set(name, node.get(name).deepCopy()));
        }
        return objectNode;
    }

    private void applyOverride(ObjectNode schema, String field, Map<String, Object> override) {
        if (override == null || override.isEmpty()) {
            return;
        }
        override.forEach((keyword, value) -> {
            JsonNode existing = schema.get(keyword);
            if (existing != null && !existing.isNull() && !narrows(keyword, existing, value)) {
                throw new ConfigurationException(
                        "override %s.%s widens the contract constraint".formatted(field, keyword));
            }
            schema.set(keyword, mapper.valueToTree(value));
        });
    }

    @SuppressWarnings("unchecked")
    private boolean narrows(String keyword, JsonNode existing, Object candidate) {
        return switch (keyword) {
            case "maximum", "maxLength", "maxItems" ->
                    asDouble(candidate) <= existing.asDouble();
            case "minimum", "minLength", "minItems" ->
                    asDouble(candidate) >= existing.asDouble();
            case "enum" -> {
                if (!existing.isArray() || !(candidate instanceof Collection<?> collection)) {
                    yield false;
                }
                Set<String> allowed = StreamSupport.stream(existing.spliterator(), false)
                        .map(JsonNode::asString)
                        .collect(java.util.stream.Collectors.toSet());
                yield allowed.containsAll(collection.stream().map(Object::toString).toList());
            }
            default -> true;
        };
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static List<String> fieldNames(ObjectNode objectNode) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = objectNode.propertyNames().iterator();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return names;
    }
}
