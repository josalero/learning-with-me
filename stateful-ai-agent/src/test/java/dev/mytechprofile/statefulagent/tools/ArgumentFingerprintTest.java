package dev.mytechprofile.statefulagent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.InterviewRound;
import dev.mytechprofile.statefulagent.business.Recommendation;
import tools.jackson.databind.json.JsonMapper;

class ArgumentFingerprintTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void jsonAndTypedFingerprintsMatch() {
        String json = "{\"applicationId\":\"APP-1\",\"stage\":\"TECH_SCREEN\"}";
        assertThat(ArgumentFingerprint.canonical("advanceApplication", json, this.jsonMapper))
                .isEqualTo(ArgumentFingerprint.advance("APP-1", HiringStage.TECH_SCREEN));
    }

    @Test
    void salaryTrailingZerosDoNotChangeTheOfferKey() {
        String fromJson = ArgumentFingerprint.canonical("extendOffer",
                "{\"applicationId\":\"APP-1\",\"level\":\"L4\",\"baseSalary\":150000.00}", this.jsonMapper);
        assertThat(fromJson).isEqualTo(ArgumentFingerprint.offer("APP-1", "L4", new BigDecimal("150000.00")));
        assertThat(ArgumentFingerprint.sha256(fromJson)).hasSize(64);
    }

    @Test
    void scorecardAndRejectUseStableNames() {
        assertThat(ArgumentFingerprint.canonical("recordScorecard",
                "{\"applicationId\":\"APP-9\",\"round\":\"ONSITE\",\"recommendation\":\"STRONG_YES\"}", this.jsonMapper))
                .isEqualTo(ArgumentFingerprint.scorecard("APP-9", InterviewRound.ONSITE, Recommendation.STRONG_YES));
        assertThat(ArgumentFingerprint.canonical("rejectApplication",
                "{\"applicationId\":\"APP-9\",\"reason\":\"BAR_NOT_MET\"}", this.jsonMapper))
                .contains("BAR_NOT_MET");
    }
}
