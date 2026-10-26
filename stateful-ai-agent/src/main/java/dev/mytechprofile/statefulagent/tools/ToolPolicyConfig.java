package dev.mytechprofile.statefulagent.tools;

import org.springframework.ai.model.tool.ToolCallConfirmationPolicy;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mytechprofile.statefulagent.business.HiringRuleException;

@Configuration
public class ToolPolicyConfig {

    @Bean
    ToolCallConfirmationPolicy riskConfirmationPolicy(RiskEvaluator riskEvaluator) {
        return (toolCall, metadata) -> riskEvaluator.confirmationRisk(toolCall.name()).atLeast(RiskLevel.MEDIUM);
    }

    @Bean
    ToolExecutionExceptionProcessor toolExecutionExceptionProcessor(ToolOutcomeSink outcomes) {
        return exception -> {
            if (exception.getCause() instanceof HiringRuleException rule) {
                outcomes.failure(rule.code(), rule.getMessage());
                return rule.getMessage();
            }
            outcomes.failure("TOOL_FAILED", "The tool could not complete the operation.");
            return "The tool could not complete the operation.";
        };
    }
}
