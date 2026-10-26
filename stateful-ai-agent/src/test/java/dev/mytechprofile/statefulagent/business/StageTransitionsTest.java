package dev.mytechprofile.statefulagent.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StageTransitionsTest {

    @Test
    void nextStageIsTheOnlyLegalMove() {
        StageTransitions.requireAdvance(HiringStage.RECRUITER_SCREEN, HiringStage.TECH_SCREEN);
        assertThatThrownBy(() -> StageTransitions.requireAdvance(HiringStage.APPLIED, HiringStage.OFFER))
                .isInstanceOf(HiringRuleException.class)
                .hasFieldOrPropertyWithValue("code", "ILLEGAL_TRANSITION");
    }

    @Test
    void terminalStageCannotAdvance() {
        assertThatThrownBy(() -> StageTransitions.requireAdvance(HiringStage.HIRED, HiringStage.OFFER))
                .isInstanceOf(HiringRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TERMINAL_STAGE");
    }

    @Test
    void offerAndOnsiteNameTheirGatingRound() {
        assertThat(StageTransitions.gatingRound(HiringStage.ONSITE)).isEqualTo(InterviewRound.TECH_SCREEN);
        assertThat(StageTransitions.gatingRound(HiringStage.OFFER)).isEqualTo(InterviewRound.ONSITE);
        assertThat(StageTransitions.gatingRound(HiringStage.TECH_SCREEN)).isNull();
    }
}
