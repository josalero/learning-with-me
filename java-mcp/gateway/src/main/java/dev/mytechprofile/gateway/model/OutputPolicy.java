package dev.mytechprofile.gateway.model;

import java.util.List;
import java.util.Locale;

import tools.jackson.databind.JsonNode;

import dev.mytechprofile.gateway.config.GatewayProperties.OutputProperties;

/**
 * Output shaping policy: allowlist-first projection with optional field masking.
 *
 * @param include allowlisted response fields; empty means passthrough (no projection)
 * @param mask fields that remain visible but redacted
 * @param maximumItems optional array cap; {@code null} means uncapped by policy
 */
public record OutputPolicy(List<String> include, List<String> mask, Integer maximumItems) {

    public static OutputPolicy none() {
        return new OutputPolicy(List.of(), List.of(), null);
    }

    public static OutputPolicy from(OutputProperties properties) {
        if (properties == null) {
            return none();
        }
        return new OutputPolicy(
                properties.include() == null ? List.of() : List.copyOf(properties.include()),
                properties.mask() == null ? List.of() : List.copyOf(properties.mask()),
                properties.maximumItems());
    }

    /** Whether allowlist projection is active. */
    public boolean isAllowlistActive() {
        return include != null && !include.isEmpty();
    }

    public boolean isIncluded(String field) {
        if (!isAllowlistActive()) {
            return true;
        }
        return include.contains(field);
    }

    public boolean isMasked(String field) {
        return mask != null && mask.contains(field);
    }

    /**
     * Redacts a field value for model-visible output.
     *
     * @param field field name
     * @param value original JSON value
     * @return redacted scalar string
     */
    public String mask(String field, JsonNode value) {
        String text = value == null || value.isNull() ? "" : value.asString();
        String lower = field.toLowerCase(Locale.ROOT);
        if (lower.contains("email") && text.contains("@")) {
            int at = text.indexOf('@');
            String local = text.substring(0, at);
            String domain = text.substring(at);
            String keep = local.isEmpty() ? "*" : local.substring(0, 1);
            return keep + "***" + domain;
        }
        if (lower.contains("phone") && text.length() >= 4) {
            return "***" + text.substring(text.length() - 4);
        }
        return "***";
    }

    public int effectiveMaximumItems(int size) {
        if (maximumItems == null || maximumItems <= 0) {
            return size;
        }
        return Math.min(size, maximumItems);
    }
}
