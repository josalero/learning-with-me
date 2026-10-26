package dev.mytechprofile.statefulagent.tools;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.business.HiringService;
import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.JobApplicationEntity;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RiskEvaluator {

    private final AgentProperties properties;
    private final HiringService hiring;
    private final JsonMapper jsonMapper;

    public RiskEvaluator(AgentProperties properties, HiringService hiring, JsonMapper jsonMapper) {
        this.properties = properties;
        this.hiring = hiring;
        this.jsonMapper = jsonMapper;
    }

    public RiskLevel confirmationRisk(String toolName) {
        if ("getApplication".equals(toolName) || "listHiringActions".equals(toolName)) {
            return RiskLevel.LOW;
        }
        return RiskLevel.MEDIUM;
    }

    public RiskLevel evaluate(String tenantId, String toolName, String argumentsJson) {
        return switch (toolName) {
            case "getApplication", "listHiringActions" -> RiskLevel.LOW;
            case "rejectApplication" -> RiskLevel.HIGH;
            case "advanceApplication" -> highStage(argumentsJson) ? RiskLevel.HIGH : RiskLevel.MEDIUM;
            case "extendOffer" -> aboveBand(tenantId, argumentsJson) ? RiskLevel.HIGH : RiskLevel.MEDIUM;
            default -> RiskLevel.MEDIUM;
        };
    }

    private boolean highStage(String argumentsJson) {
        String stage = text(read(argumentsJson), "stage");
        if (stage.isBlank()) {
            return true;
        }
        try {
            return this.properties.getRisk().getHighStages().contains(HiringStage.valueOf(stage));
        }
        catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private boolean aboveBand(String tenantId, String argumentsJson) {
        JsonNode node = read(argumentsJson);
        JsonNode salary = node.get("baseSalary");
        if (salary == null || !salary.isNumber()) {
            return true;
        }
        try {
            JobApplicationEntity application = this.hiring.requireApplication(tenantId, text(node, "applicationId"));
            return salary.decimalValue().compareTo(application.getBandMax()) > 0;
        }
        catch (RuntimeException exception) {
            return true;
        }
    }

    private JsonNode read(String argumentsJson) {
        try {
            return this.jsonMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
        }
        catch (RuntimeException exception) {
            return this.jsonMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    public static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }
}
