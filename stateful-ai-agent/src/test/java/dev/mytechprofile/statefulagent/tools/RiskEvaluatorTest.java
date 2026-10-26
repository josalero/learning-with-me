package dev.mytechprofile.statefulagent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import tools.jackson.databind.json.JsonMapper;

class RiskEvaluatorTest {

    private final RiskEvaluator evaluator = evaluator();

    @Test
    void readsStayLowAndLateStagesOrUnknownSalaryAreHigh() {
        assertThat(this.evaluator.confirmationRisk("getApplication")).isEqualTo(RiskLevel.LOW);
        assertThat(this.evaluator.confirmationRisk("advanceApplication")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(this.evaluator.evaluate("tenant-a", "advanceApplication",
                "{\"applicationId\":\"APP-1\",\"stage\":\"TECH_SCREEN\"}")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(this.evaluator.evaluate("tenant-a", "advanceApplication",
                "{\"applicationId\":\"APP-1\",\"stage\":\"ONSITE\"}")).isEqualTo(RiskLevel.HIGH);
        assertThat(this.evaluator.evaluate("tenant-a", "advanceApplication",
                "{\"applicationId\":\"APP-1\",\"stage\":\"\"}")).isEqualTo(RiskLevel.HIGH);
        assertThat(this.evaluator.evaluate("tenant-a", "extendOffer",
                "{\"applicationId\":\"missing\",\"level\":\"L4\"}")).isEqualTo(RiskLevel.HIGH);
        assertThat(this.evaluator.evaluate("tenant-a", "rejectApplication",
                "{\"applicationId\":\"APP-1\",\"reason\":\"BAR_NOT_MET\"}")).isEqualTo(RiskLevel.HIGH);
    }

    private static RiskEvaluator evaluator() {
        AgentProperties properties = new AgentProperties();
        properties.getRisk().setHighStages(List.of(HiringStage.ONSITE, HiringStage.OFFER));
        return new RiskEvaluator(properties, null, JsonMapper.builder().build());
    }
}
