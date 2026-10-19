package dev.mytechprofile.aigateway.recruiting.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionRepository;
import dev.mytechprofile.aigateway.gateway.tools.GovernedTool;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import dev.mytechprofile.aigateway.recruiting.candidate.EvidenceRepository;
import dev.mytechprofile.aigateway.recruiting.recommendation.RecommendationDraftRepository;
import dev.mytechprofile.aigateway.recruiting.transition.TransitionRequestRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers the five recruiting tools as {@code GovernedTool} beans. The tool classes stay package-private.
 */
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
