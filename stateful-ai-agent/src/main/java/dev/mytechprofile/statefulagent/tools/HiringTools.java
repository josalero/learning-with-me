package dev.mytechprofile.statefulagent.tools;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.business.HiringActionEntity;
import dev.mytechprofile.statefulagent.business.HiringActionRepository;
import dev.mytechprofile.statefulagent.business.HiringRuleException;
import dev.mytechprofile.statefulagent.business.HiringService;
import dev.mytechprofile.statefulagent.business.HiringService.HiringResult;
import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.InterviewRound;
import dev.mytechprofile.statefulagent.business.InterviewScorecardEntity;
import dev.mytechprofile.statefulagent.business.InterviewScorecardRepository;
import dev.mytechprofile.statefulagent.business.JobApplicationEntity;
import dev.mytechprofile.statefulagent.business.JobOfferEntity;
import dev.mytechprofile.statefulagent.business.JobOfferRepository;
import dev.mytechprofile.statefulagent.business.OfferResponse;
import dev.mytechprofile.statefulagent.business.Recommendation;
import dev.mytechprofile.statefulagent.business.RejectReason;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HiringTools {

    private static final Logger log = LoggerFactory.getLogger(HiringTools.class);

    public static final String TENANT_ID = "tenantId";
    public static final String SESSION_ID = "sessionId";
    public static final String APPROVAL_ID = "approvalId";
    public static final String CALL_KEYS = "callKeyByHash";

    private final HiringService hiring;
    private final HiringActionRepository actions;
    private final InterviewScorecardRepository scorecards;
    private final JobOfferRepository offers;
    private final FailureGate failures;
    private final JsonMapper jsonMapper;

    public HiringTools(HiringService hiring, HiringActionRepository actions, InterviewScorecardRepository scorecards,
            JobOfferRepository offers, FailureGate failures, JsonMapper jsonMapper) {
        this.hiring = hiring;
        this.actions = actions;
        this.scorecards = scorecards;
        this.offers = offers;
        this.failures = failures;
        this.jsonMapper = jsonMapper;
    }

    @Tool(description = "Read stage, latest scorecards, and offer status. Changes nothing.")
    public String getApplication(String applicationId, ToolContext toolContext) {
        String tenantId = required(toolContext, TENANT_ID);
        JobApplicationEntity application = this.hiring.requireApplication(tenantId, applicationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationId", application.getId());
        body.put("roleTitle", application.getRoleTitle());
        body.put("stage", application.getStage().name());
        this.scorecards.findByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId).stream()
                .findFirst()
                .ifPresent(card -> body.put("latestRecommendation", card.getRecommendation().name()));
        this.offers.findByTenantIdAndApplicationId(tenantId, applicationId)
                .ifPresent(row -> body.put("offerStatus", row.getStatus()));
        String recommendation = body.containsKey("latestRecommendation") ? ", latest scorecard " + body.get("latestRecommendation") : "";
        String offer = body.containsKey("offerStatus") ? ", offer " + body.get("offerStatus") : "";
        log.info("{} is at {}{}{}.", application.getId(), application.getStage(), recommendation, offer);
        return this.jsonMapper.writeValueAsString(body);
    }

    @Tool(description = "List hiring actions for an application. Changes nothing.")
    public String listHiringActions(String applicationId, ToolContext toolContext) {
        String tenantId = required(toolContext, TENANT_ID);
        this.hiring.requireApplication(tenantId, applicationId);
        List<Map<String, String>> rows = this.actions.findByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId)
                .stream()
                .limit(20)
                .map(this::actionRow)
                .toList();
        return this.jsonMapper.writeValueAsString(Map.of("applicationId", applicationId, "actions", rows));
    }

    @Tool(description = "Move an application to the next stage.", requiresConfirmation = true)
    public String advanceApplication(String applicationId, HiringStage stage, ToolContext toolContext) {
        try {
            String key = idempotencyKey("advanceApplication", ArgumentFingerprint.advance(applicationId, stage), toolContext);
            HiringResult result = this.hiring.advance(required(toolContext, TENANT_ID), applicationId, stage, key);
            return written("ADVANCE", result);
        }
        catch (HiringRuleException exception) {
            throw failure("advanceApplication", exception);
        }
    }

    @Tool(description = "Record the interviewer's recommendation for the current round.", requiresConfirmation = true)
    public String recordScorecard(String applicationId, InterviewRound round, Recommendation recommendation, ToolContext toolContext) {
        try {
            if (this.failures.shouldFail()) {
                throw new HiringRuleException("TRANSIENT_FAILURE", "The scorecard could not be saved. Retry the approval.");
            }
            String key = idempotencyKey("recordScorecard", ArgumentFingerprint.scorecard(applicationId, round, recommendation), toolContext);
            HiringResult result = this.hiring.recordScorecard(required(toolContext, TENANT_ID), applicationId, round, recommendation, key);
            return written("SCORECARD", result);
        }
        catch (HiringRuleException exception) {
            throw failure("recordScorecard", exception);
        }
    }

    @Tool(description = "Extend an offer with a level and base salary.", requiresConfirmation = true)
    public String extendOffer(String applicationId, String level, BigDecimal baseSalary, ToolContext toolContext) {
        try {
            if (this.failures.shouldFail()) {
                throw new HiringRuleException("TRANSIENT_FAILURE", "The offer could not be saved. Retry the approval.");
            }
            String key = idempotencyKey("extendOffer", ArgumentFingerprint.offer(applicationId, level, baseSalary), toolContext);
            HiringResult result = this.hiring.extendOffer(required(toolContext, TENANT_ID), applicationId, level, baseSalary, key);
            return written("OFFER_EXTENDED", result);
        }
        catch (HiringRuleException exception) {
            throw failure("extendOffer", exception);
        }
    }

    @Tool(description = "Record whether the candidate accepted or declined the offer.", requiresConfirmation = true)
    public String recordOfferResponse(String applicationId, OfferResponse response, ToolContext toolContext) {
        try {
            String key = idempotencyKey("recordOfferResponse", ArgumentFingerprint.response(applicationId, response), toolContext);
            HiringResult result = this.hiring.recordOfferResponse(required(toolContext, TENANT_ID), applicationId, response, key);
            return written("OFFER_RESPONSE", result);
        }
        catch (HiringRuleException exception) {
            throw failure("recordOfferResponse", exception);
        }
    }

    @Tool(description = "Reject an application with a fixed reason.", requiresConfirmation = true)
    public String rejectApplication(String applicationId, RejectReason reason, ToolContext toolContext) {
        try {
            String key = idempotencyKey("rejectApplication", ArgumentFingerprint.reject(applicationId, reason), toolContext);
            HiringResult result = this.hiring.reject(required(toolContext, TENANT_ID), applicationId, reason, key);
            return written("REJECT", result);
        }
        catch (HiringRuleException exception) {
            throw failure("rejectApplication", exception);
        }
    }

    public List<HiringActionEntity> actions(String tenantId, String applicationId) {
        return this.actions.findByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId);
    }

    public List<InterviewScorecardEntity> scorecards(String tenantId, String applicationId) {
        return this.scorecards.findByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId);
    }

    public java.util.Optional<JobOfferEntity> offer(String tenantId, String applicationId) {
        return this.offers.findByTenantIdAndApplicationId(tenantId, applicationId);
    }

    private Map<String, String> actionRow(HiringActionEntity entry) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("action", entry.getAction());
        row.put("fromStage", entry.getFromStage().name());
        row.put("toStage", entry.getToStage().name());
        if (entry.getReason() != null) {
            row.put("reason", entry.getReason());
        }
        return row;
    }

    private String written(String action, HiringResult result) {
        return this.jsonMapper.writeValueAsString(Map.of(
                "status", result.created() ? "APPLIED" : "REPLAYED",
                "action", action,
                "stage", result.stage().name()));
    }

    @SuppressWarnings("unchecked")
    private static String idempotencyKey(String toolName, String canonical, ToolContext toolContext) {
        String sessionId = required(toolContext, SESSION_ID);
        String approvalId = required(toolContext, APPROVAL_ID);
        Object raw = toolContext.getContext().get(CALL_KEYS);
        if (!(raw instanceof Map<?, ?> map)) {
            throw new HiringRuleException("MISSING_APPROVAL", "A sensitive tool ran without an approval context.");
        }
        Object callKey = map.get(ArgumentFingerprint.sha256(canonical));
        if (!(callKey instanceof String key) || key.isBlank()) {
            throw new HiringRuleException("TOOL_CALL_MISMATCH", "The tool call does not match the approved request.");
        }
        return sessionId + ":" + approvalId + ":" + key;
    }

    private static ToolExecutionException failure(String toolName, HiringRuleException exception) {
        ToolDefinition definition = DefaultToolDefinition.builder().name(toolName).description(toolName).inputSchema("{}").build();
        return new ToolExecutionException(definition, exception);
    }

    private static String required(ToolContext toolContext, String key) {
        Object value = toolContext.getContext().get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new HiringRuleException("MISSING_CONTEXT", "The tool is missing its server-side context.");
        }
        return text;
    }
}
