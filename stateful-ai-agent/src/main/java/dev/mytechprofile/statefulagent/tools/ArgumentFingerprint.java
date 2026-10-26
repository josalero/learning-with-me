package dev.mytechprofile.statefulagent.tools;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.InterviewRound;
import dev.mytechprofile.statefulagent.business.OfferResponse;
import dev.mytechprofile.statefulagent.business.Recommendation;
import dev.mytechprofile.statefulagent.business.RejectReason;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class ArgumentFingerprint {

    private ArgumentFingerprint() {
    }

    public static String canonical(String toolName, String argumentsJson, JsonMapper jsonMapper) {
        JsonNode node = read(argumentsJson, jsonMapper);
        return switch (toolName) {
            case "advanceApplication" -> advance(text(node, "applicationId"), text(node, "stage"));
            case "recordScorecard" -> scorecard(text(node, "applicationId"), text(node, "round"), text(node, "recommendation"));
            case "extendOffer" -> offer(text(node, "applicationId"), text(node, "level"), amount(node, "baseSalary"));
            case "recordOfferResponse" -> response(text(node, "applicationId"), text(node, "response"));
            case "rejectApplication" -> reject(text(node, "applicationId"), text(node, "reason"));
            case "getApplication", "listHiringActions" -> toolName + "|" + text(node, "applicationId");
            default -> toolName + "|" + argumentsJson;
        };
    }

    public static String advance(String applicationId, HiringStage stage) {
        return advance(applicationId, stage.name());
    }

    public static String advance(String applicationId, String stage) {
        return "advanceApplication|" + applicationId + "|" + stage;
    }

    public static String scorecard(String applicationId, InterviewRound round, Recommendation recommendation) {
        return scorecard(applicationId, round.name(), recommendation.name());
    }

    public static String scorecard(String applicationId, String round, String recommendation) {
        return "recordScorecard|" + applicationId + "|" + round + "|" + recommendation;
    }

    public static String offer(String applicationId, String level, BigDecimal salary) {
        return offer(applicationId, level, salary == null ? "" : salary.stripTrailingZeros().toPlainString());
    }

    public static String offer(String applicationId, String level, String salary) {
        return "extendOffer|" + applicationId + "|" + level + "|" + salary;
    }

    public static String response(String applicationId, OfferResponse response) {
        return response(applicationId, response.name());
    }

    public static String response(String applicationId, String response) {
        return "recordOfferResponse|" + applicationId + "|" + response;
    }

    public static String reject(String applicationId, RejectReason reason) {
        return reject(applicationId, reason.name());
    }

    public static String reject(String applicationId, String reason) {
        return "rejectApplication|" + applicationId + "|" + reason;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static JsonNode read(String argumentsJson, JsonMapper jsonMapper) {
        try {
            return jsonMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
        }
        catch (RuntimeException exception) {
            return jsonMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static String amount(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return "";
        }
        return value.decimalValue().stripTrailingZeros().toPlainString();
    }
}
