package dev.mytechprofile.gateway.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;

/**
 * Validates model-supplied tool arguments against the compiled JSON Schema.
 *
 * <p>Validates the JSON Schema vocabulary emitted by the bundled connectors:
 * object/array/scalar types, required and additional properties, enums, numeric
 * bounds, string constraints, array constraints, and nested schemas.
 */
@Component
public class ArgumentValidator {

    /**
     * Merges defaults, validates, and returns the argument map used for injection.
     *
     * @param tool compiled tool
     * @param arguments raw MCP arguments
     * @return validated map including defaults
     */
    public Map<String, Object> validate(CompiledTool tool, Map<String, Object> arguments) {
        Map<String, Object> withDefaults = new LinkedHashMap<>(tool.defaults());
        if (arguments != null) {
            withDefaults.putAll(arguments);
        }

        validateValue("$", withDefaults, tool.inputSchema());
        return withDefaults;
    }

    private void validateValue(String path, Object value, JsonNode schema) {
        if (value == null) {
            if (!allowsNull(schema)) {
                fail(path, "must not be null");
            }
            return;
        }

        String type = schema.path("type").asString("");
        switch (type) {
            case "object" -> validateObject(path, value, schema);
            case "array" -> validateArray(path, value, schema);
            case "string" -> validateString(path, value, schema);
            case "integer" -> validateNumber(path, value, schema, true);
            case "number" -> validateNumber(path, value, schema, false);
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    fail(path, "must be a boolean");
                }
            }
            case "", "null" -> {
                // Empty type is permitted for schemas containing only enum/const constraints.
            }
            default -> fail(path, "unsupported schema type: " + type);
        }

        if (schema.has("enum") && schema.get("enum").isArray()) {
            boolean matched = false;
            for (JsonNode candidate : schema.get("enum")) {
                if (jsonScalarEquals(candidate, value)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                fail(path, "must be one of " + schema.get("enum"));
            }
        }
        if (schema.has("const") && !jsonScalarEquals(schema.get("const"), value)) {
            fail(path, "must equal " + schema.get("const"));
        }
    }

    private void validateObject(String path, Object value, JsonNode schema) {
        if (!(value instanceof Map<?, ?>)) {
            fail(path, "must be an object");
        }
        Map<?, ?> map = (Map<?, ?>) value;
        JsonNode properties = schema.path("properties");
        Set<String> allowed = properties.isObject()
                ? properties.propertyNames().stream().collect(java.util.stream.Collectors.toSet())
                : Set.of();

        if (schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean()) {
            for (Object key : map.keySet()) {
                if (!allowed.contains(String.valueOf(key))) {
                    fail(child(path, String.valueOf(key)), "unknown field (additionalProperties is false)");
                }
            }
        }

        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode node : required) {
                String name = node.asString();
                Object field = map.get(name);
                if (field == null || (field instanceof String string && string.isBlank())) {
                    fail(child(path, name), "required field is missing");
                }
            }
        }
        if (properties.isObject()) {
            properties.propertyNames().forEach(name -> {
                if (map.containsKey(name)) {
                    validateValue(child(path, name), map.get(name), properties.get(name));
                }
            });
        }
    }

    private void validateArray(String path, Object value, JsonNode schema) {
        if (!(value instanceof List<?>)) {
            fail(path, "must be an array");
        }
        List<?> list = (List<?>) value;
        if (schema.has("minItems") && list.size() < schema.get("minItems").asInt()) {
            fail(path, "contains fewer than " + schema.get("minItems").asInt() + " items");
        }
        if (schema.has("maxItems") && list.size() > schema.get("maxItems").asInt()) {
            fail(path, "contains more than " + schema.get("maxItems").asInt() + " items");
        }
        if (schema.path("uniqueItems").asBoolean(false)
                && new HashSet<>(list).size() != list.size()) {
            fail(path, "items must be unique");
        }
        JsonNode itemSchema = schema.path("items");
        if (!itemSchema.isMissingNode()) {
            for (int index = 0; index < list.size(); index++) {
                validateValue(path + '[' + index + ']', list.get(index), itemSchema);
            }
        }
    }

    private void validateString(String path, Object value, JsonNode schema) {
        if (!(value instanceof String)) {
            fail(path, "must be a string");
        }
        String string = (String) value;
        if (schema.has("minLength") && string.length() < schema.get("minLength").asInt()) {
            fail(path, "length is below " + schema.get("minLength").asInt());
        }
        if (schema.has("maxLength") && string.length() > schema.get("maxLength").asInt()) {
            fail(path, "length exceeds " + schema.get("maxLength").asInt());
        }
        if (schema.has("pattern")) {
            try {
                if (!Pattern.compile(schema.get("pattern").asString()).matcher(string).find()) {
                    fail(path, "does not match required pattern");
                }
            } catch (PatternSyntaxException ex) {
                fail(path, "catalog contains an invalid pattern");
            }
        }
    }

    private void validateNumber(String path, Object value, JsonNode schema, boolean integer) {
        if (!(value instanceof Number)) {
            fail(path, integer ? "must be an integer" : "must be a number");
        }
        Number number = (Number) value;
        double numeric = number.doubleValue();
        if (integer && numeric != Math.rint(numeric)) {
            fail(path, "must be an integer");
        }
        if (schema.has("maximum") && numeric > schema.get("maximum").asDouble()) {
            fail(path, "exceeds maximum " + schema.get("maximum").asDouble());
        }
        if (schema.has("exclusiveMaximum") && numeric >= schema.get("exclusiveMaximum").asDouble()) {
            fail(path, "must be below " + schema.get("exclusiveMaximum").asDouble());
        }
        if (schema.has("minimum") && numeric < schema.get("minimum").asDouble()) {
            fail(path, "below minimum " + schema.get("minimum").asDouble());
        }
        if (schema.has("exclusiveMinimum") && numeric <= schema.get("exclusiveMinimum").asDouble()) {
            fail(path, "must be above " + schema.get("exclusiveMinimum").asDouble());
        }
        if (schema.has("multipleOf")) {
            double divisor = schema.get("multipleOf").asDouble();
            if (divisor <= 0 || Math.abs(numeric / divisor - Math.rint(numeric / divisor)) > 1.0e-9) {
                fail(path, "must be a multiple of " + divisor);
            }
        }
    }

    private boolean allowsNull(JsonNode schema) {
        if ("null".equals(schema.path("type").asString())) {
            return true;
        }
        JsonNode type = schema.path("type");
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if ("null".equals(candidate.asString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean jsonScalarEquals(JsonNode candidate, Object value) {
        if (value instanceof Number number && candidate.isNumber()) {
            return Double.compare(candidate.asDouble(), number.doubleValue()) == 0;
        }
        if (value instanceof Boolean bool && candidate.isBoolean()) {
            return candidate.asBoolean() == bool;
        }
        return candidate.isString()
                ? candidate.asString().equals(String.valueOf(value))
                : candidate.toString().equals(String.valueOf(value));
    }

    private String child(String path, String field) {
        return "$".equals(path) ? field : path + '.' + field;
    }

    private void fail(String path, String detail) {
        throw new GatewayException(new GatewayError.ValidationError(path, detail));
    }
}
