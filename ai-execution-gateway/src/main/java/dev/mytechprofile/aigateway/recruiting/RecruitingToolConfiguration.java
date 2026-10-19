package dev.mytechprofile.aigateway.recruiting;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.persistence.EvidenceRepository;
import dev.mytechprofile.aigateway.persistence.ExecutionRepository;
import dev.mytechprofile.aigateway.persistence.RecommendationDraftRepository;
import dev.mytechprofile.aigateway.persistence.TransitionRequestRepository;
import dev.mytechprofile.aigateway.tools.GovernedTool;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RecruitingToolConfiguration {

    @Bean
    GovernedTool getCandidateSummaryTool(CandidateRepository candidates, JsonMapper json) {
        return new GetCandidateSummaryTool(candidates, json);
    }

    @Bean
    GovernedTool searchEvidenceTool(EvidenceRepository evidence, ExecutionRepository executions, JsonMapper json) {
        return new SearchEvidenceTool(evidence, executions, json);
    }

    @Bean
    GovernedTool draftRecommendationTool(RecommendationDraftRepository drafts, JsonMapper json) {
        return new DraftRecommendationTool(drafts, json);
    }

    @Bean
    GovernedTool requestStatusTransitionTool(TransitionRequestRepository requests, JsonMapper json) {
        return new RequestStatusTransitionTool(requests, json);
    }

    @Bean
    GovernedTool executeStatusTransitionTool() {
        return new ExecuteStatusTransitionTool();
    }
}
