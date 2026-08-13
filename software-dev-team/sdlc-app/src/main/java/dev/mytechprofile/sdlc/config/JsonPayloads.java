package dev.mytechprofile.sdlc.config;

/**
 * Pulls a JSON object out of model output that mixed thinking, markdown fences, or prose.
 *
 * <p>Sample: thinking {@code "ok\n{\"decision\":\"APPROVE\"}"} yields the object string.
 */
public final class JsonPayloads {

    private JsonPayloads() {}

    /**
     * Returns the first JSON object in {@code raw}, or {@code raw} trimmed when it already is one.
     *
     * @param raw assistant text or thinking
     * @return JSON object or {@code null} when none is present
     */
    public static String firstObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int fence = trimmed.indexOf("```");
        if (fence >= 0) {
            int jsonTag = trimmed.indexOf("```json", fence);
            int startFence = jsonTag >= 0 ? jsonTag + 7 : fence + 3;
            int endFence = trimmed.indexOf("```", startFence);
            if (endFence > startFence) {
                String inner = firstObject(trimmed.substring(startFence, endFence));
                if (inner != null) {
                    return inner;
                }
            }
        }
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Returns true when {@code text} has non-blank characters.
     *
     * @param text assistant content
     * @return whether parsers can see a payload
     */
    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
