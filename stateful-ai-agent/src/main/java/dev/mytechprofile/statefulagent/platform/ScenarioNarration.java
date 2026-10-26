package dev.mytechprofile.statefulagent.platform;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a paused tool call into one sentence a scenario log can print.
 */
public final class ScenarioNarration {

    private ScenarioNarration() {
    }

    public static String action(String toolName, String argumentsJson, JsonMapper jsonMapper) {
        JsonNode node = read(argumentsJson, jsonMapper);
        String applicationId = text(node, "applicationId");
        return switch (toolName) {
            case "getApplication" -> "read " + applicationId;
            case "listHiringActions" -> "list hiring actions for " + applicationId;
            case "advanceApplication" -> "advance " + applicationId + " to " + text(node, "stage");
            case "recordScorecard" -> "record a " + text(node, "recommendation") + " scorecard for " + applicationId
                    + " on " + text(node, "round");
            case "extendOffer" -> "extend a " + text(node, "level") + " offer of " + text(node, "baseSalary") + " for "
                    + applicationId;
            case "recordOfferResponse" -> "ACCEPTED".equals(text(node, "response"))
                    ? "record that " + applicationId + " accepted the offer"
                    : "record that " + applicationId + " declined the offer";
            case "rejectApplication" -> "reject " + applicationId + " (" + text(node, "reason") + ")";
            default -> toolName;
        };
    }

    public static String risk(String level) {
        return switch (level) {
            case "HIGH" -> "high risk, so only an approver can allow it";
            case "MEDIUM" -> "medium risk, so a recruiter can allow it";
            case "LOW" -> "low risk";
            default -> level;
        };
    }

    private static JsonNode read(String argumentsJson, JsonMapper jsonMapper) {
        try {
            return jsonMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        }
        catch (RuntimeException exception) {
            return jsonMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            return "unknown";
        }
        return value.asString();
    }
}
