package dev.mytechprofile.aigateway.recruiting.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.mytechprofile.aigateway.gateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import dev.mytechprofile.aigateway.recruiting.recommendation.Recommendation;
import dev.mytechprofile.aigateway.recruiting.recommendation.RecommendationDraftEntity;
import dev.mytechprofile.aigateway.recruiting.recommendation.RecommendationDraftRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code draft_recommendation}: saves the agent's suggestion and returns its {@code draftId}.
 */
class DraftRecommendationTool implements GovernedTool {

    private final RecommendationDraftRepository drafts;
    private final JsonMapper json;

    DraftRecommendationTool(RecommendationDraftRepository drafts, JsonMapper json) {
        this.drafts = drafts;
        this.json = json;
    }

    @Override
    public String name() {
        return "draft_recommendation";
    }

    @Override
    public String execute(JsonNode arguments, ToolExecutionContext context) {
        RecommendationDraftEntity draft = new RecommendationDraftEntity();
        draft.setId(UUID.randomUUID());
        draft.setExecutionId(context.executionId());
        draft.setCandidateId(arguments.path("candidateId").asString());
        draft.setRecommendation(Recommendation.valueOf(arguments.path("recommendation").asString()));
        draft.setRationale(arguments.path("rationale").asString());
        draft.setCitedEvidenceIds(stringList(arguments.path("citedEvidenceIds")));
        drafts.save(draft);
        return json.createObjectNode().put("draftId", draft.getId().toString()).toString();
    }

    private static List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }
}
